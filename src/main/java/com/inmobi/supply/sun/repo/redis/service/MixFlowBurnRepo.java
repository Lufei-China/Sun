package com.inmobi.supply.sun.repo.redis.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

/**
 * @author william.wang
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class MixFlowBurnRepo implements BaseRepo<Integer> {
    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Getter
    private volatile Double mixFlowTodayBurn;

    @Getter
    private volatile Map<String,Integer> mixFlowTodayEvent = new HashMap<>();

    @Override
    public Flux<Integer> loadRepo() {
        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.MIX_FLOW_TODAY_BURN_KEY.getPrefix())
                .switchIfEmpty(Mono.just(""))
                .flatMap(burn -> {
                    if (StringUtils.isNotBlank(burn)) {
                        mixFlowTodayBurn = Double.parseDouble(burn);
                    }
                    else {
                        mixFlowTodayBurn = null;
                    }
                    return Mono.just(0);
                })
                .flatMap(
                        f -> loadMixFlowTodayEvent()
                )
                .onErrorResume(exception -> {
                    log.error("MixFlowBurnRepo loadRepo error:" + exception.getMessage());
                    return Mono.just(0);
                }).flux();
    }

    private Mono<Integer> loadMixFlowTodayEvent() {
        Map<String,Integer> mixFlowTodayEventNew = new HashMap<>();
        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.MIX_FLOW_TODAY_EVENT_KEY.getPrefix())
                .switchIfEmpty(Mono.just(""))
                .flatMap(str -> {
                    Map<String, Integer> map = JSON.parseObject(str, new TypeReference<Map<String, Integer>>(){});
                    if (map != null) {
                        mixFlowTodayEventNew.putAll(map);
                    }
                    mixFlowTodayEvent = mixFlowTodayEventNew;
                    return Mono.just(0);
                }).onErrorResume(exception -> {
                    LogUtils.logTask("load mixFlowTodayEvent error {}", exception);
                    return Mono.just(0);
                });
    }

}
