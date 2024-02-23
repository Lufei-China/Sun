package com.inmobi.supply.sun.service.preProc.validator;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.validator.module.*;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Validator模块
 *
 * @author Gavin.Xu
 */

@Service
@Slf4j
public class Validator2Service extends Worker2<NormalizerDataType, AsyncServletResponse> {
    @Autowired
    Worker2<ValidatorDataType,AsyncServletResponse> nextWorker;
    @Autowired
    ValidatorRequestField validatorRequestField;
    @Autowired
    ValidatorPap validatorPap;
    @Autowired
    ValidatorDevice validatorDevice;
    @Autowired
    ValidatorIdMapping validatorIdMapping;
    @Autowired
    ValidatorIntegration validatorIntegration;
    @Autowired
    ValidatorTraffic validatorTraffic;
    @Autowired
    ValidatorHeaders validatorHeaders;


    @Override
    protected Mono<? extends AsyncServletResponse> apply(NormalizerDataType normalizerDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            LogUtils.logInfo(runTimeContext.getRequestId(),"Validator2Service");
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Validator");
                return validatorRequestField.workMono(runTimeContext)
                        .flatMap(ctx -> validatorTraffic.workMono(runTimeContext)
                                .flatMap(ctx1 -> validatorPap.workMono(runTimeContext)
                                    .flatMap(ctx2 -> validatorDevice.workMono(runTimeContext)
                                            .flatMap(ctx3 -> validatorIdMapping.workMono(runTimeContext)
                                                    .flatMap(ctx4 -> validatorHeaders.workMono(runTimeContext)
                                                        .flatMap(ctx5 -> validatorIntegration.workMono(runTimeContext)))))));
            }
            return Mono.just(new ValidatorDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new ValidatorDataType());
        })).transform(nextWorker.transform());
    }

}