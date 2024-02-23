package com.inmobi.supply.sun.service;

import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import reactor.core.publisher.Mono;

/**
 * module worker interface
 *
 * @author remi.li
 */

public interface ModuleWorker<T> {
    default Mono<T> workMono(RunTimeContext runTimeContext) {
        if (skipIfDebug()) {
            //TODO BY REMI
            return processMono(runTimeContext);
        }
        return processMono(runTimeContext);
    }

    Mono<T> processMono(RunTimeContext runTimeContext);

    default boolean skipIfDebug() {
        return false;
    }
}