package com.inmobi.supply.sun.service.preProc.validator;

import java.util.Map;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.FlowLimitDataType;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.novice.NoviceService;
import com.inmobi.supply.sun.service.preProc.validator.module.*;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Validator模块
 *
 * @author Gavin.Xu
 */

@Service
@Slf4j
public class ValidatorService extends Worker<FlowLimitDataType, ValidatorDataType> {
    @Autowired
    NoviceService noviceService;
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

    @Override
    public Flux<ValidatorDataType> doProcess(Context context, Flux<FlowLimitDataType> flowLimitDataTypeFlux, Map<Integer, RunTimeContext> mapNewContext) {
        return flowLimitDataTypeFlux
                .flatMap(normalizerDataType -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Validator");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    return validatorRequestField.workMono(runTimeContext)
                            .flatMap(ctx -> validatorPap.workMono(runTimeContext)
                                    .flatMap(ctx1 -> validatorDevice.workMono(runTimeContext)
                                            .flatMap(ctx2 -> validatorIdMapping.workMono(runTimeContext)
                                                    .flatMap(ctx3 -> {
                                                        Mono<ValidatorDataType> validatorDataTypeMono = validatorIntegration.workMono(runTimeContext);
                                                        mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                                        return validatorDataTypeMono;
                                                    }))));
                });
    }

    @Override
    public Worker getNextProc() {
        return noviceService;
    }
}