package com.inmobi.supply.sun.service.postProc.format;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.postProc.format.module.AdFormatterWorker;
import com.inmobi.supply.sun.service.postProc.format.module.TemplateSelector;
import com.inmobi.supply.sun.service.postProc.format.module.TrackingUrlGen;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 最终的打包服务
 *
 * @author remi.li
 */
@Service
@Slf4j
public class Envelope2Service extends Worker2<AuctionDataType, AsyncServletResponse> {
    @Autowired
    private TrackingUrlGen trackingUrlGen;

    @Autowired
    private AdFormatterWorker adFormatterWorker;

    @Autowired
    private TemplateSelector templateSelector;

    @Autowired
    private Worker2<BaseResponse,AsyncServletResponse> nextWorker;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(AuctionDataType auctionDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "EnvelopeService");
                LogUtils.logInfo(runTimeContext.getRequestId(),"enter Auction2Service");
                return trackingUrlGen.workMono(runTimeContext)
                        .flatMap(trackingUrlGenRunTime -> adFormatterWorker.workMono(runTimeContext)
                                .flatMap(envelopRunTime -> templateSelector.workMono(runTimeContext)
                                        .flatMap(iEnvelop -> iEnvelop.workMono(runTimeContext))));
            }

           return Mono.just(BaseResponse.newBuilder().build());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程

            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(BaseResponse.newBuilder().build());
        })).transform(nextWorker.transform());
    }

}