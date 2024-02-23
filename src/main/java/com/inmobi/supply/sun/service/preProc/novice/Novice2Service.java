package com.inmobi.supply.sun.service.preProc.novice;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.config.dynamic.*;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.NappDataType;
import com.inmobi.supply.sun.model.dataFlow.NoviceDataType;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.NoviceRepo;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichGeo;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.NOVICE_HOUR_WHITE_FLAG;
import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.NOVICE_SERVICE_BP_FLAG;
import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.NOVICE_SERVICE_NEWER_FLAG;
import static com.inmobi.supply.sun.utils.RandomUtils.*;


/**
 * 限流模块
 *
 * @date 20200512
 */

@Service
@Slf4j
public class Novice2Service extends Worker2<ValidatorDataType, AsyncServletResponse> {
    @Autowired
    Worker2<NoviceDataType, AsyncServletResponse> nextWorker;
    @Autowired
    NoviceRepo noviceRepo;
    @Autowired
    EnrichGeo enrichGeo;
    @Autowired
    private PrometheusOverlord prometheusOverlord;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(ValidatorDataType validatorDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getIsTestPlacement() == 1) {
                    PerfUtils.perf("sun.skipNovice.count", "skip", "success");
                    return Mono.just(new NoviceDataType());
                }
                return enrichGeo.workMono(runTimeContext)
                        .flatMap(enrichmentDataType -> {
                            PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "NoviceService");
                            /**
                             * SDK流量保留
                             */
                            if (runTimeContext.getRequestType() == RequestType.SDK) {
                                PerfUtils.perf("novice", "status", "sdk white");
                                return Mono.just(new NoviceDataType());
                            }
                            /**
                             * 指定 bundle 和 广告位 不过bp
                             */
                            if (DynamicListConfig.BRUSH_FLOW_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())
                                    && DynamicListConfig.BRUSH_FLOW_BUNDLE_LIST.get().contains(runTimeContext.getRequestBundleId())) {
                                PerfUtils.perf("sun.brush.bp");
                                return Mono.just(new NoviceDataType());
                            }

                            if (DynamicListConfig.WHITE_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())) {
                                PerfUtils.perf("sun.white.placement","type","bp");
                                return Mono.just(new NoviceDataType());
                            }



                            if (DynamicBooleanConfig.NOVICE_SERVICE_FLAG.get()) {
                                Long placementId = runTimeContext.getPlacementId();
                                //开发者白名单
                                if (DynamicListConfig.NOVICE_WHITE_ACCOUNT.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                                    return Mono.just(new NoviceDataType());
                                }
                                //最近一小时，rcpm > rcost的广告位
                                if (NOVICE_HOUR_WHITE_FLAG.get()) {
                                    if (noviceRepo.isHourList(placementId)) {
                                        PerfUtils.perf("novice", "status", "hour white");
                                        return Mono.just(new NoviceDataType());
                                    }
                                }
                                //7日内创建的广告位，新手drop 20%
                                if (noviceRepo.isNewer(placementId) && NOVICE_SERVICE_NEWER_FLAG.get()) {
                                    if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_NEWER_RATE.get())) {
                                        //runTimeContext.getExperimentFlags().add("AF-S-914");
                                        PerfUtils.perf("novice", "status", "drop by newer");
                                        return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_NEWER, runTimeContext));
                                    }
                                    return Mono.just(new NoviceDataType());
                                }

                                /**
                                 * 第二种情况为新手池：（Drop 50%）
                                 * 查询所有广告位，过去7日内，流量情况全部满足以下规则的则为新手池广告位：
                                 * 1，过去7日内Rcpm小于当前exchange或者demand的动态rcost  2，过去7日内有流量的日期不大于3
                                 *
                                 */
                                Float exchangeRcost = DynamicFloatConfig.NOVICE_EXCHANGE_RCOST.get();
                                Float demandRcost = DynamicFloatConfig.NOVICE_DEMAND_RCOST.get();
                                boolean belowExchangeRcost = noviceRepo.isLow(placementId.toString(), exchangeRcost);
                                boolean belowDemandRcost = noviceRepo.isLow(placementId.toString(), demandRcost);
                                if (noviceRepo.isRequestDay(String.valueOf(placementId)) && NOVICE_SERVICE_NEWER_FLAG.get() && !(belowExchangeRcost && belowDemandRcost)) {
                                    LogUtils.logInfo(runTimeContext.getRequestId(),"dropNewer2 belowExchangeRcost:{} belowDemandRcost:{} placementId:{}",belowExchangeRcost,belowDemandRcost,placementId);
                                    if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_NEWER2_RATE.get())) {
                                        PerfUtils.perf("novice", "status", "drop by newer2");
                                        return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_NEWER, runTimeContext));
                                    }
                                    return Mono.just(new NoviceDataType());
                                }

                                //7日内rcpm < rcost的广告位，drop
                                if (NOVICE_SERVICE_BP_FLAG.get()) {
                                    if (DynamicListConfig.NOVICE_WHITE.get().contains(placementId)) {
                                        PerfUtils.perf("novice", "status", "not drop by white");
                                        runTimeContext.getNappExperiments().add(416);
                                        return Mono.just(new NoviceDataType());
                                    }
                                    LogUtils.logInfo(runTimeContext.getRequestId(), "dynamic exchangeRcost {}  demandRcost{}", exchangeRcost, demandRcost);
                                    prometheusOverlord.getExchangeRcost().set(exchangeRcost);
                                    prometheusOverlord.getDemandRcost().set(demandRcost);
                                    if (belowExchangeRcost && belowDemandRcost) {
                                        PerfUtils.perf("novice", "status", "ready drop by BP");
                                        if (dropByHour(DynamicMapConfig.NOVICE_DROP_HOUR_BP_RATE.get())) {
                                            //runTimeContext.getExperimentFlags().add("AF-S-912");
                                            PerfUtils.perf("novice", "status", "drop by BP");
                                            return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_BP, runTimeContext));
                                        }
                                    } else if (belowExchangeRcost) {
                                        PerfUtils.perf("novice", "status", "ready drop by BP for Exchange");
                                        if (dropByHour(DynamicMapConfig.NOVICE_DROP_HOUR_BP_RATE.get())) {
                                            PerfUtils.perf("novice", "status", "drop by BP for Exchange");
                                            LogUtils.logInfo(runTimeContext.getRequestId(), "drop by BP for Exchange {} ",runTimeContext.getPlacementId());
                                            runTimeContext.setBelowExchangeRcost(belowExchangeRcost);
                                        }
                                    } else if (belowDemandRcost) {
                                        PerfUtils.perf("novice", "status", "ready drop by BP for Demand");
                                        if (dropByHour(DynamicMapConfig.NOVICE_DROP_HOUR_BP_RATE.get())) {
                                            PerfUtils.perf("novice", "status", "drop by BP for Demand");
                                            LogUtils.logInfo(runTimeContext.getRequestId(), "drop by BP for Demand {} ",runTimeContext.getPlacementId());
                                            runTimeContext.setBelowDemandRcost(belowDemandRcost);
                                        }
                                    } else {
                                        PerfUtils.perf("novice", "status", "no drop by BP");
                                    }
                                }
                            }
                            return Mono.just(new NoviceDataType());
                        });
            }
            return Mono.just(new NoviceDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new NoviceDataType());
        })).transform(nextWorker.transform());
    }

}