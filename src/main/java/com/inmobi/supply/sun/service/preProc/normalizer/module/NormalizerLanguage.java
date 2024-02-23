package com.inmobi.supply.sun.service.preProc.normalizer.module;

import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Gavin.xu
 * 2020.5.18
 * 设备language
 */
@Service
@Slf4j
public class NormalizerLanguage implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        return Mono.just(0);
    }
}
