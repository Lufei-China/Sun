package com.inmobi.supply.sun.config.redis;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * redis crud baseRepository
 *
 * @param <T>
 */
public interface BaseRedisRepository<T> {
    Mono<Boolean> save(T t);

    Mono<Boolean> update(T t);

    Mono<Boolean> delete(Object id);

    Mono<T> findById(Object id);

    Flux<T> findAll();

}
