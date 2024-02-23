package com.inmobi.supply.sun.service.postProc.format;

import java.util.Map;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.postProc.ResponseWriterFactory;
import com.inmobi.supply.sun.service.postProc.format.module.AdFormatterWorker;
import com.inmobi.supply.sun.service.postProc.format.module.TemplateSelector;
import com.inmobi.supply.sun.service.postProc.format.module.TrackingUrlGen;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 最终的打包服务
 *
 * @author remi.li
 */
@Service
@Slf4j
public class EnvelopeService extends Worker<AuctionDataType, BaseResponse> {
    @Autowired
    private TrackingUrlGen trackingUrlGen;

    @Autowired
    private AdFormatterWorker adFormatterWorker;

    @Autowired
    private TemplateSelector templateSelector;

    @Autowired
    private ResponseWriterFactory responseWriterFactory;

    @Override
    public Flux<BaseResponse> doProcess(Context context, Flux<AuctionDataType> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .flatMap(bidRequest -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Envelope");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    return trackingUrlGen.workMono(runTimeContext)
                            .flatMap(trackingUrlGenRunTime -> adFormatterWorker.workMono(runTimeContext)
                                    .flatMap(envelopRunTime -> templateSelector.workMono(runTimeContext)
                                            .flatMap(iEnvelop -> {
                                Mono<BaseResponse> baseResponse = iEnvelop.workMono(runTimeContext);
                                mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                return baseResponse;
                            })));
                });
    }

    @Override
    public Worker getNextProc() {
        return responseWriterFactory;
    }
}