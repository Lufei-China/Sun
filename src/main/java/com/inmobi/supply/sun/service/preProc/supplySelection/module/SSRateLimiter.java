package com.inmobi.supply.sun.service.preProc.supplySelection.module;

import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import reactor.core.publisher.Mono;

/**
 * supply selection ratelimiter
 *
 * @author remi.li
 */
public class SSRateLimiter implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        return Mono.just(0);
    }
}
