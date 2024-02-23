package com.inmobi.supply.sun.service.preProc.normalizer;


import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.normalizer.module.*;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * normalizer模块
 *
 * @author Gavin.Xu
 */
@Service
@Slf4j
public class Normalizer2Service extends Worker2<byte[], AsyncServletResponse> {
    @Autowired
    Worker2<NormalizerDataType,AsyncServletResponse> nextWorker;

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
    protected Mono<? extends AsyncServletResponse> apply(byte[] bytes) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Normalizer");
                runTimeContext.setInputStream(bytes);
                return normalizerBodyHeaders.workMono(runTimeContext)
                        .flatMap(ctx -> normalizerHostAndUrl.workMono(runTimeContext)
                                .flatMap(ctx1 -> normalizerRequest.workMono(runTimeContext)
                                        .flatMap(ctx2 -> normalizerRequestId.workMono(runTimeContext)
                                                .flatMap(ctx3 -> normalizerLatLong.workMono(runTimeContext)))));
            }
            return Mono.just(new NormalizerDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new NormalizerDataType());
        })).transform(nextWorker.transform());
    }
}