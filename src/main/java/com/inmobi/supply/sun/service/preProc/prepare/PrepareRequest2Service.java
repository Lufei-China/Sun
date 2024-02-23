package com.inmobi.supply.sun.service.preProc.prepare;


import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.dataFlow.PrepareRequestDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareAdPools;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidFloor2;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidRequest;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareForward;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 初始化广告请求
 * 产出BidRequest对象，接下来用于请求广告池
 *
 * @author remi.li
 */
@Service
@Slf4j
public class PrepareRequest2Service extends Worker2<EnrichmentDataType, AsyncServletResponse> {
    @Autowired
    private Worker2<PrepareRequestDataType,AsyncServletResponse> nextWorker;
    @Autowired
    private PrepareBidFloor2 prepareBidFloor2;
    @Autowired
    private PrepareAdPools prepareAdPools;
    @Autowired
    private PrepareBidRequest prepareBidRequest;
    @Autowired
    private PrepareForward prepareForward;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(EnrichmentDataType enrichmentDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                LogUtils.logInfo(runTimeContext.getRequestId(),"PrepareRequest2Service");
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "PrepareRequestService");
                return prepareBidFloor2.workMono(runTimeContext)
                        .flatMap(ctx -> prepareAdPools.workMono(runTimeContext)
                                .flatMap(ctx2 -> prepareForward.workMono(runTimeContext))
                                    .flatMap(ctx1 -> prepareBidRequest.workMono(runTimeContext)))
                                        .flatMap(r -> Mono.just(new PrepareRequestDataType()));
            }
            return Mono.just(new PrepareRequestDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new PrepareRequestDataType());
        })).transform(nextWorker.transform());
    }

}