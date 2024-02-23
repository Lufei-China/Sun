package com.inmobi.supply.sun.service.preProc.validator.module;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * @Description ValidatorHeaders
 * @Date 2022/5/23 14:59
 * @Author william.wang
 */
@Component
public class ValidatorHeaders implements ModuleWorker<Integer> {

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        // 4.0.0强制要求开启gzip
        if (RequestType.API_4_0.equals(runTimeContext.getRequestType())) {
            Map<String, String> headers = runTimeContext.getHeaders();
            String acceptEncoding = headers.get("accept-encoding");
            if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                return Mono.error(new SunException(TermCode.NO_GZIP, runTimeContext));            }
        }
        return Mono.just(1);
    }
}
