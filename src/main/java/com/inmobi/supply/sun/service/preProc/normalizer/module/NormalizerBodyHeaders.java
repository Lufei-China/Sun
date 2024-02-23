package com.inmobi.supply.sun.service.preProc.normalizer.module;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.RequestHeader.GATEWAY_IP;

/**
 * extract headers and body string/byte[] of ServerHttpRequest
 *
 * @author Gavin.xu
 * @Date 2020.5.18
 **/
@Service
@Slf4j
public class NormalizerBodyHeaders implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            HttpHeaders headers = runTimeContext.getServerRequest().headers().asHttpHeaders();
            if (CollectionUtils.isNotEmpty(headers.keySet())) {
                for (String name : headers.keySet()) {
                    runTimeContext.getHeaders().put(StringUtils.lowerCase(name), headers.getFirst(name));
                }
            }
            String bodyString = new String(runTimeContext.getInputStream());
            runTimeContext.setRequestBody(bodyString);
            return Mono.just(0);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}