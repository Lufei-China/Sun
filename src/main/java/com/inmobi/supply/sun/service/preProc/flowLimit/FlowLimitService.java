package com.inmobi.supply.sun.service.preProc.flowLimit;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.FlowLimitDataType;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.validator.ValidatorService;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.signature.qual.PolySignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;

/**
 * 功能概述
 * className:      FlowLimitService
 * package:        com.inmobi.supply.sun.service.preProc.flowLimit
 * author:         Gavin.Xu
 * date:           2021/8/23
 */
@Service
@Slf4j
public class FlowLimitService extends Worker<NormalizerDataType, FlowLimitDataType> {

    @Autowired
    ValidatorService validatorService;

    @Override
    public Flux<FlowLimitDataType> doProcess(Context context, Flux<NormalizerDataType> normalizerDataTypeFlux, Map<Integer, RunTimeContext> mapNewContext) {
        return normalizerDataTypeFlux.flatMap(normalizerDataType -> {
            PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "FlowLimitService");
            RunTimeContext runTimeContext = getUpperRunTimeContext(context);
            //do flow limit
            boolean limit = false;
            if (runTimeContext.getRequestType() != RequestType.SDK){
                Entry entry = null;
                try {
                    entry = SphU.entry("sunEndPoint");
                } catch (BlockException e1) {
                    PerfUtils.perf("sunEndPoint.block");
                    limit = true;
                } catch (Exception e2) {
                    log.error("sunEntrance try flow limit error:{}", e2);
                } finally {
                    if (entry != null) {
                        entry.exit();
                    }
                }
            }
            mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
            if (limit){
                return Mono.error(new SunException(TermCode.FLOW_LIMIT, runTimeContext));
            }
            return Mono.just(new FlowLimitDataType());
        });
    }

    @Override
    public Worker getNextProc() {
        return validatorService;
    }
}
