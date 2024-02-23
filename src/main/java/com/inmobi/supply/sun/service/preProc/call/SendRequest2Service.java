package com.inmobi.supply.sun.service.preProc.call;

import cn.inmobi.pb.api.*;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiter;
import com.inmobi.supply.sun.adPool.IAdPool;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import com.inmobi.supply.sun.constant.BidDropReason;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.SaturnDsp;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.PrepareRequestDataType;
import com.inmobi.supply.sun.model.dataFlow.SendRequestDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.postProc.auction.module.CheckBidResponse;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.REQUEST_TIME_OUT_TAKE;
import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.REQUEST_TIME_OUT_THRESHOLD;

/**
 * send request
 * input BidRequest
 * output BidResponse
 *
 * @author remi.li
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class SendRequest2Service extends Worker2<PrepareRequestDataType, AsyncServletResponse> {
    @Autowired
    PrometheusOverlord prometheusOverlord;

    @Autowired
    private Worker2<SendRequestDataType, AsyncServletResponse> nextWorker;

    @Autowired
    private CheckBidResponse checkBidResponse;

    @Autowired
    private SandboxService sandboxService;

    @Autowired
    private MixFlowRateService mixFlowRateService;


    @Override
    protected Mono<? extends AsyncServletResponse> apply(PrepareRequestDataType prepareRequestDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            LogUtils.logInfo(runTimeContext.getRequestId(),"SendRequest2Service");
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "SendRequest");
                List<IAdPool> adPoolList = runTimeContext.getAdPoolList();
                BidRequest bidRequest = runTimeContext.getBidRequest();
                if (bidRequest.getTest().equals("1")) {
                    //直接返回mock的广告
                    BidResponse bidResponse = sandboxService.mockAd(runTimeContext);
                    if (bidResponse != null && !bidResponse.equals(BidResponse.getDefaultInstance())) {
                        //优先出测试广告
                        runTimeContext.getValidBidResponseList().add(bidResponse);
                    }
                    return Mono.just(new SendRequestDataType());
                }

                if (null != runTimeContext.getPlacementModel() && 1 == runTimeContext.getPlacementModel().getMixFlowFlag()) {
                    PerfUtils.perf("sun.mixFlowRate", Tag.of("placementId", String.valueOf(runTimeContext.getPlacementId())), Tag.of("status", "total"));
                    //混量广告位返回指定的素材和价格
                    BidResponse bidResponse = mixFlowRateService.buildBidResponse(runTimeContext);
                    if (bidResponse != null && !bidResponse.equals(BidResponse.getDefaultInstance())) {
                        PerfUtils.perf("sun.mixFlowRate", Tag.of("placementId", String.valueOf(runTimeContext.getPlacementId())), Tag.of("status", "success"));
                        runTimeContext.getValidBidResponseList().add(bidResponse);
                        return Mono.just(new SendRequestDataType());
                    }
                }
                LogUtils.logInfo(runTimeContext.getRequestId(), "bidRequest {}", bidRequest);
                return Flux.fromIterable(adPoolList)
                        .flatMap(iAdPool -> {
                            try {
                                /*Duration timeOut = iAdPool.timeout();
                                if (DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getAppModel().getPubAccountId())) {
                                    if (runTimeContext.getAdType() == AdType.FEED) {
                                        timeOut = Duration.ofMillis(DynamicIntegerConfig.SATURN_BAIDU_FEED_AD_POOL.get().longValue());
                                    } else {
                                        timeOut = Duration.ofMillis(DynamicIntegerConfig.SATURN_BAIDU_AD_POOL.get().longValue());
                                    }
                                }*/
                                /**
                                 * 取动态的timeout
                                 */
                                int timeout = bidRequest.getTimeout() == 0 ? 200 : bidRequest.getTimeout();
                                //针对 timeout 大于 250ms 的统一减 20ms 处理 apollo 进行配置
                                if(timeout >= REQUEST_TIME_OUT_THRESHOLD.get()){
                                    timeout = timeout - REQUEST_TIME_OUT_TAKE.get();
                                }
                                int finalTimeout = timeout;
                                Mono<BidResponse> adPoolBidRespFlux = ((Mono<BidResponse>) RateLimiter.supplyWithRateLimiter(iAdPool.rateLimiter(),
                                        () -> {
                                            PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                    Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "sendRequest"),
                                                    Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                            BidRequest bidRequestWithRequestAdpool = runTimeContext.getBidRequest().toBuilder().addSourceTypeList(iAdPool.sourceType().getNumber()).build();
                                            runTimeContext.setBidRequest(bidRequestWithRequestAdpool);

                                            return iAdPool.sendRequest(bidRequest).defaultIfEmpty(BidResponse.getDefaultInstance());
                                        },
                                        () -> {
                                            PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                    Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "rateLimiter"),
                                                    Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                            return Mono.just(BidResponse.getDefaultInstance());
                                        }))
                                        //timeout will propagate a TimeoutException
                                        .timeout(Duration.ofMillis(timeout))
                                        .onErrorResume(throwable -> {
                                            if (throwable instanceof TimeoutException
                                                    || throwable instanceof StatusRuntimeException
                                                    || throwable instanceof InterruptedException) {
                                                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                        Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "timeout"),
                                                        Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                runTimeContext.getAdpoolTermReason().put(iAdPool, TermCode.AD_POOL_REQUEST_TIMEOUT);
                                                LogUtils.logError(runTimeContext.getRequestId(), "placement " + runTimeContext.getPlacementId() + " sendRequest timeout " + finalTimeout + " error {}", throwable);
                                                if (throwable instanceof TimeoutException){
                                                    PerfUtils.perf("sun.req.timeout",Tag.of("placement",runTimeContext.getPlacementId().toString()));
                                                }
                                                return Mono.just(BidResponse.getDefaultInstance());
                                            } else {
                                                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                        Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "error"),
                                                        Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                LogUtils.logError(runTimeContext.getRequestId(), "placement " + runTimeContext.getPlacementId() + " ad pool request error {}", throwable);
                                                runTimeContext.getAdpoolTermReason().put(iAdPool, TermCode.AD_POOL_REQUEST_ERROR);
                                                return Mono.just(BidResponse.getDefaultInstance());
                                            }
                                        }).elapsed().flatMap(tuple2 -> {
                                            if (tuple2.getT1() > finalTimeout) {
                                                LogUtils.logInfo(runTimeContext.getRequestId(), "placement {} setTimeout {} useTime {}", runTimeContext.getPlacementId(), finalTimeout, tuple2.getT1());
                                            }
                                            if (iAdPool.sourceType() == SourceType.PERF) {
                                                PerfUtils.timer("perf.latency", tuple2.getT1());
                                            }
                                            if (iAdPool.sourceType() == SourceType.SATURN) {
                                                PerfUtils.timer("saturn.latency", tuple2.getT1());
                                                //  记录saturn 返回的时间
                                                runTimeContext.setSaturnProcessTime(tuple2.getT1().intValue());
                                                if (finalTimeout > 200) {
                                                    PerfUtils.perf("saturn.latency.gt200", Tag.of("type", "total"));
                                                    if (tuple2.getT2().getProcessingTimeMs() > 200 && tuple2.getT2().getAdsCount() != 0) {
                                                        PerfUtils.perf("saturn.latency.gt200", Tag.of("type", "fill"));
                                                    }
                                                }
                                                // 打印广点通有广告返回的latency，用来排查问题
                                                if (tuple2.getT2().getAdsCount() != 0 ) {
                                                    LogUtils.logInfo(runTimeContext.getRequestId(),"{} channel fillAd cost {}ms", tuple2.getT2().getAds(0).getSaturnChannelId(),tuple2.getT1());
                                                    PerfUtils.timer("sun.exchange.filled.latency",tuple2.getT1(),Tag.of("channel",tuple2.getT2().getAds(0).getSaturnChannelId()));
                                                    runTimeContext.setChannel(tuple2.getT2().getAds(0).getSaturnChannelId());
                                                }
                                            }
                                            return Mono.just(tuple2.getT2());
                                        });
                                return adPoolBidRespFlux;
                            } catch (Exception e) {
                                //手误，把rateLimiter的值写成0或者负数，砍头
                                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                        Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "error"),
                                        Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                return Mono.just(BidResponse.getDefaultInstance());
                            }
                        })
                        .filter(bidResponse -> {
                            BidDropReason bidDropReason = checkBidResponse.checkBidResponse(runTimeContext, bidResponse);
                            if (bidDropReason.log()) {
                                log.error("bidResponse invalid {} dropReason {}", bidResponse, bidDropReason);
                            }
                            return bidDropReason == BidDropReason.VALID;
                        })
                        .doOnCancel(() -> {
                            LogUtils.logWarn(runTimeContext.getRequestId(), "{} wait response cancel by reactor", runTimeContext.getPlacementId());
                        })
                        .collectList()
                        .defaultIfEmpty(new ArrayList<>())
                        .flatMap(list -> {
                            if (CollectionUtils.isEmpty(list)) {
                                return Mono.error(new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext));
                            }
                            list = checkBidResponse.repeatCreative(runTimeContext, list);
                            runTimeContext.getValidBidResponseList().addAll(list);
                            return Mono.just(new SendRequestDataType());
                        });
            }
            return Mono.just(new SendRequestDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new SendRequestDataType());
        })).transform(nextWorker.transform());
    }

}