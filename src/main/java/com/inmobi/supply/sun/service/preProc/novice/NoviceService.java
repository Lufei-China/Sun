package com.inmobi.supply.sun.service.preProc.novice;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.dataFlow.NoviceDataType;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.NoviceRepo;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichGeo;

import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;

import static com.inmobi.supply.component.constant.TermCode.*;
import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.*;
import static com.inmobi.supply.sun.utils.RandomUtils.dropByPercent;


/**
 * 限流模块
 *
 * @date 20200512
 */

@Service
@Slf4j
public class NoviceService extends Worker<ValidatorDataType, NoviceDataType> {
    @Autowired
    NoviceRepo noviceRepo;
    @Autowired
    EnrichGeo enrichGeo;

    @Override
    public Flux<NoviceDataType> doProcess(Context context, Flux<ValidatorDataType> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc.flatMap(
                validatorDataType -> {
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    return enrichGeo.workMono(runTimeContext)
                            .flatMap(enrichmentDataType -> {
                                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "NoviceService");
                                mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                if(DynamicBooleanConfig.NOVICE_SERVICE_FLAG.get()){
                                    Long placementId = runTimeContext.getPlacementId();
                                    //开发者白名单
                                    if (DynamicListConfig.NOVICE_WHITE_ACCOUNT.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                                        return Mono.just(new NoviceDataType());
                                    }
                                    //最近一小时，rcpm > rcost的广告位
                                    if (NOVICE_HOUR_WHITE_FLAG.get()){
                                        if (noviceRepo.isHourList(placementId)) {
                                            PerfUtils.perf("novice", "status", "hour white");
                                            return Mono.just(new NoviceDataType());
                                        }
                                    }
                                    //TODO 待研究
                                    /*if (noviceRepo.isAlLow(runTimeContext.getPlacementId())) {
                                        if (dropByPercent(DynamicIntegerConfig.NOVICE_AL_PER.get())) {
                                            PerfUtils.perf("novice", "status", "drop by al");
                                            return Mono.error(new SunException(DROP_BY_NOVICE_AL, runTimeContext));
                                        } else {
                                            PerfUtils.perf("novice", "status", "no drop by al");
                                        }

                                    } else {
                                        PerfUtils.perf("novice", "status", "big al");
                                    }*/
                                    //7日内创建的广告位，新手drop 20%
                                    if (noviceRepo.isNewer(placementId) && NOVICE_SERVICE_NEWER_FLAG.get()) {
                                        if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_NEWER_RATE.get())) {
                                            //runTimeContext.getExperimentFlags().add("AF-S-914");
                                            PerfUtils.perf("novice", "status", "drop by newer");
                                            return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_NEWER, runTimeContext));
                                        }
                                    }
                                    //7日内netRev为0的广告位， drop 80%
                                    /*if (noviceRepo.isNetRev0(placementId) && NOVICE_SERVICE_ZERO_NETREV_FLAG.get()) {
                                        if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_ZERO_NETREV_RATE.get())) {
                                            //runTimeContext.getExperimentFlags().add("AF-S-913");
                                            PerfUtils.perf("novice", "status", "drop by zeroNetRev");
                                            return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_NETREV, runTimeContext));
                                        }
                                    }*/
                                    //7日内rcpm < rcost的广告位，drop
                                    if (noviceRepo.isReqBpLow(placementId) && NOVICE_SERVICE_BP_FLAG.get()) {
                                        PerfUtils.perf("novice", "status", "ready drop by BP");
                                        if (DynamicListConfig.NOVICE_WHITE.get().contains(placementId)) {
                                            PerfUtils.perf("novice", "status", "not drop by white");
                                            runTimeContext.getNappExperiments().add(416);
                                            return Mono.just(new NoviceDataType());
                                        }
                                        if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_BP_RATE.get())) {
                                            //runTimeContext.getExperimentFlags().add("AF-S-912");
                                            PerfUtils.perf("novice", "status", "drop by BP");
                                            return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_BP, runTimeContext));
                                        }
                                    }
                                    // bp值计算 此处代码架构有问题以后调整
                                    //  判断是属于7天内bp值 数据量小的时候使用redis，大时候使用布隆过滤器
                                    /*String cityCode = null;
                                    if (runTimeContext.getGeo() != null) {
                                        cityCode = runTimeContext.getGeo().getCityCode();
                                        if (StringUtils.isBlank(cityCode)) {
                                            cityCode = runTimeContext.getGeo().getProvinceCode();
                                        }
                                    }
                                    if (noviceRepo.isCityLow(runTimeContext.getPlacementId(), cityCode) && NOVICE_SERVICE_BP_CITY_FLAG.get()) {
                                        if (DynamicListConfig.NOVICE_WHITE.get().contains(placementId)) {
                                            runTimeContext.getNappExperiments().add(416);
                                            return Mono.just(new NoviceDataType());
                                        }
                                        if (dropByPercent(DynamicIntegerConfig.NOVICE_DROP_BP_CITY_RATE.get())) {
                                            PerfUtils.perf("novice", "status", "drop by city BP");
                                            return Mono.error(new SunException(TermCode.DROP_BY_NOVICE_BP, runTimeContext));
                                        }
                                    }*/
                                }
                                return Mono.just(new NoviceDataType());
                            });
                }
        );
    }

    @Override
    public Worker getNextProc() {
        return null;
    }
}