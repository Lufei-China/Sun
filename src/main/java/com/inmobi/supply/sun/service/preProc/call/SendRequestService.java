package com.inmobi.supply.sun.service.preProc.call;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.api.BidRequest;
import cn.inmobi.pb.api.BidResponse;
import cn.inmobi.pb.api.SourceType;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiter;
import com.inmobi.supply.sun.adPool.IAdPool;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import com.inmobi.supply.sun.constant.BidDropReason;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.NoviceRepo;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.postProc.auction.AuctionService;
import com.inmobi.supply.sun.service.postProc.auction.module.CheckBidResponse;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.context.Context;

import static com.inmobi.supply.component.constant.TermCode.DROP_BY_NAPP;
import static com.inmobi.supply.component.constant.TermCode.DROP_BY_NOVICE_BP;
import static com.inmobi.supply.sun.utils.RandomUtils.dropByPercent;

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
public class SendRequestService extends Worker<BidRequest, BidResponse> {
    @Autowired
    PrometheusOverlord prometheusOverlord;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CheckBidResponse checkBidResponse;

    @Autowired
    private SandboxService sandboxService;

    @Autowired
    private NoviceRepo noviceRepo;

    @Override
    public Flux<BidResponse> doProcess(Context context, Flux<BidRequest> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .flatMap(bidRequest -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "SendRequest");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);

                    List<IAdPool> adPoolList = runTimeContext.getAdPoolList();
                    mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                    if (bidRequest.getTest().equals("1")) {
                        //直接返回mock的广告
                        BidResponse bidResponse = sandboxService.mockAd(runTimeContext);
                        if (bidResponse != null && !bidResponse.equals(BidResponse.getDefaultInstance())) {
                            //优先出测试广告
                            return Flux.just(bidResponse);
                        }
                    }


                    Flux<BidResponse> bidResponseListFlux = Flux.fromIterable(adPoolList)
                            //.parallel(adPoolList.size())
                            //.runOn(scheduler)
                            .flatMap(iAdPool -> {
                                try {
                                    Duration timeOut = iAdPool.timeout();
                                    if(DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getPlacementModel().getAccountId())){
                                        if(runTimeContext.getAdType()== AdType.FEED){
                                            timeOut = Duration.ofMillis(DynamicIntegerConfig.SATURN_BAIDU_FEED_AD_POOL.get().longValue());
                                        }else {
                                            timeOut = Duration.ofMillis(DynamicIntegerConfig.SATURN_BAIDU_AD_POOL.get().longValue());
                                        }
                                    }
//                                    if (runTimeContext.getPlacementModel().getTimeOut() != null) {
//                                        timeOut = Duration.ofMillis(runTimeContext.getPlacementModel().getTimeOut());
//                                    }
                                    Mono<BidResponse> adPoolBidRespFlux = ((Mono<BidResponse>) RateLimiter.supplyWithRateLimiter(iAdPool.rateLimiter(),
                                            () -> {
                                                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                        Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "sendRequest"),
                                                        Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                BidRequest bidRequestWithRequestAdpool = runTimeContext.getBidRequest().toBuilder().addSourceTypeList(iAdPool.sourceType().getNumber()).build();
                                                runTimeContext.setBidRequest(bidRequestWithRequestAdpool);
                                                log.info("iAdPool bidRequest deviceId:{}",bidRequest);
                                                return iAdPool.sendRequest(bidRequest).defaultIfEmpty(BidResponse.getDefaultInstance());
                                            },
                                            () -> {
                                                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                        Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "rateLimiter"),
                                                        Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                return Mono.just(BidResponse.getDefaultInstance());
                                            }))
                                            //timeout will propagate a TimeoutException
                                            .timeout(timeOut)
                                            .onErrorResume(throwable -> {
                                                if (throwable instanceof TimeoutException
                                                        || throwable instanceof StatusRuntimeException
                                                        || throwable instanceof InterruptedException) {
                                                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                            Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "timeout"),
                                                            Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                    runTimeContext.getAdpoolTermReason().put(iAdPool, TermCode.AD_POOL_REQUEST_TIMEOUT);
                                                    return Mono.just(BidResponse.getDefaultInstance());
                                                } else {
                                                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST,
                                                            Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "error"),
                                                            Tag.of(PerfConstant.AD_POOL, iAdPool.sourceType().name()));
                                                    log.error("ad pool request error", throwable);
                                                    runTimeContext.getAdpoolTermReason().put(iAdPool, TermCode.AD_POOL_REQUEST_ERROR);
                                                    return Mono.just(BidResponse.getDefaultInstance());
                                                }
                                            }).elapsed().flatMap(tuple2 -> {
                                                if (iAdPool.sourceType() == SourceType.PERF) {
                                                    PerfUtils.timer("perf.latency", tuple2.getT1());
                                                }
                                                if (iAdPool.sourceType() == SourceType.SATURN) {
                                                    PerfUtils.timer("saturn.latency", tuple2.getT1());
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
                            //.sorted((o1, o2) -> 0)
                            .filter(bidResponse -> {
                                BidDropReason bidDropReason = checkBidResponse.checkBidResponse(runTimeContext, bidResponse);
                                if (bidDropReason.log()){
                                    log.error("bidResponse invalid {} dropReason {}", bidResponse, bidDropReason);
                                }
                                return bidDropReason == BidDropReason.VALID;
                            });
                    return bidResponseListFlux;
                });
    }

    @Override
    public Worker getNextProc() {
        return auctionService;
    }
}