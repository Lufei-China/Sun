package com.inmobi.supply.sun.service.preProc.flowLimit;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.FlowLimitDataType;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.dataFlow.NoviceDataType;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * 功能概述
 * className:      FlowLimitService
 * package:        com.inmobi.supply.sun.service.preProc.flowLimit
 * author:         Gavin.Xu
 * date:           2021/8/23
 */
//@Service
@Slf4j
public class FlowLimit2Service extends Worker2<NoviceDataType, AsyncServletResponse> {

    @Autowired
    Worker2<FlowLimitDataType,AsyncServletResponse> nextWorker;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(NoviceDataType noviceDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "FlowLimitService");
                //do flow limit
                boolean limit = false;
                if (runTimeContext.getRequestType() != RequestType.SDK) {
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
                if (limit) {
                    return Mono.error(new SunException(TermCode.FLOW_LIMIT, runTimeContext));
                }
            }
            return Mono.just(new FlowLimitDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new FlowLimitDataType());
        })).transform(nextWorker.transform());
    }

}
