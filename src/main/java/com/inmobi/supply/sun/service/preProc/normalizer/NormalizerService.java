package com.inmobi.supply.sun.service.preProc.normalizer;

import java.util.Map;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.flowLimit.FlowLimitService;
import com.inmobi.supply.sun.service.preProc.normalizer.module.*;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * normalizer模块
 *
 * @author Gavin.Xu
 */
@Service
@Slf4j
public class NormalizerService extends Worker<byte[], NormalizerDataType> {
    @Autowired
    FlowLimitService flowLimitService;
    @Autowired
    NormalizerBodyHeaders normalizerBodyHeaders;
    @Autowired
    NormalizerHostAndUrl normalizerHostAndUrl;
    @Autowired
    NormalizerRequest normalizerRequest;
    @Autowired
    NormalizerRequestId normalizerRequestId;
    @Autowired
    NormalizerCookie normalizerCookie;
    @Autowired
    NormalizerLanguage normalizerLanguage;
    @Autowired
    NormalizerLatLong normalizerLatLong;

    @Override
    public Flux<NormalizerDataType> doProcess(Context context, Flux<byte[]> inputStreamFlux, Map<Integer, RunTimeContext> mapNewContext) {
        return inputStreamFlux
                .flatMap(inputStream -> {
            PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Normalizer");
            RunTimeContext runTimeContext = getUpperRunTimeContext(context);
            runTimeContext.setInputStream(inputStream);
            return normalizerBodyHeaders.workMono(runTimeContext)
                        .flatMap(ctx -> normalizerHostAndUrl.workMono(runTimeContext)
                                .flatMap(ctx1 -> normalizerRequest.workMono(runTimeContext)
                                        .flatMap(ctx2 -> normalizerRequestId.workMono(runTimeContext)
                                                .flatMap(ctx6 -> {
                                                    Mono<NormalizerDataType> normalizerDataTypeMono = normalizerLatLong.workMono(runTimeContext);
                                                    mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                                    return normalizerDataTypeMono;
                                                }))));
        });
    }

    @Override
    public Worker getNextProc() {
        return flowLimitService;
    }
}