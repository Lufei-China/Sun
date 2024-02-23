package com.inmobi.supply.sun.repo.redis.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 动态底价redis模块
 * @author remi.li
 */
@Slf4j
@Repository
public class DynamicFloorRedisImpl {
    private static final long EXPIRE_SECONDS = 7200; //2小时过期

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    public Mono<Long> incrementByKey(String key, String numKey, long value) {
        return stringTemplate.expire(key, Duration.ofSeconds(EXPIRE_SECONDS))
                .then(stringTemplate.expire(numKey, Duration.ofSeconds(EXPIRE_SECONDS)))
                .then(stringTemplate.opsForValue().increment(key, value))
                .then(stringTemplate.opsForValue().increment(numKey, 1));
    }

    public Mono<String> findById(String id) {
        return stringTemplate.opsForValue().get(id);
    }

    public Mono<List<String>> findByMultiId(String ...ids) {
        return stringTemplate.opsForValue().multiGet(Arrays.asList(ids));
    }
}