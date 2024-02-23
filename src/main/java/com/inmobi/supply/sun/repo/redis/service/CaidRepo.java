package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@SuppressWarnings("unchecked")
public class CaidRepo implements BaseRepo<Integer> {
    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Getter
    private volatile Set<String> caidSet;

    @Getter
    private volatile Map<String, String> caidParamVersion;

    @Override
    public Flux<Integer> loadRepo() {
        Set<String> caidSetNew = new HashSet();
        Map<String, String> caidParamVersionNew = new HashMap<>();
        return reactiveRedisTemplate.opsForSet()
                .members(RedisKey.CAID_SET.getPrefix())
                .flatMap(caid -> {
                    caidSetNew.add(caid);
                    return reactiveRedisTemplate.opsForValue()
                            .get(RedisKey.CAID_TWELVE_PARAM.of(caid))
                            .flatMap(value -> {
                                caidParamVersionNew.put(caid, value);
                                return Mono.just(0);
                            }).thenMany(Flux.just(0).flatMap(integer -> {
                                caidSet = caidSetNew;
                                caidParamVersion = caidParamVersionNew;
                                return Flux.just(0);
                            })).onErrorResume(exception -> {
                                log.error("CaidRepo loadRepo error:" + exception.getMessage());
                                return Flux.just(0);
                            });
                });
    }
}
