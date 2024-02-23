package com.inmobi.supply.sun.repo.redis.service;

import com.alibaba.fastjson.JSON;
import com.framework.util.json.ObjectMapperUtils;
import com.google.common.collect.Sets;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicMapConfig;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.PlacementRedisImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * placement信息本地缓存+redis Repo
 *
 * @author remi.li
 */

@Service
@Slf4j
public class PlacementRepo implements BaseRepo<Integer> {
    @Autowired
    PlacementRedisImpl placementRedis;

    //populate placeCache with an entirely new hashMap(using volatile),other than updating the existing hashMap(using concurrentHashMap)
    private volatile Map<Long, PlacementModel> placeCache;

    private volatile Set<String> blockCache = Sets.newHashSet();

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    @Deprecated
    public Flux<Integer> loadRepo() {
        log.info("enter PlacementRepo load");
        Map<Long, PlacementModel> newPlaceCache = new HashMap<>();
        return placementRedis.findAll()
                .switchIfEmpty(Flux.error(new Exception("no elements")))
                .flatMap(placementModel -> {
                    newPlaceCache.put(placementModel.getId(), placementModel);
                    return Flux.just(0);
                })
                .thenMany(Flux.just(0).flatMap(integer -> {
                    placeCache = newPlaceCache;
                    log.info("placeCache size={}", placeCache.size());
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    log.error("PlacementRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });
    }

    public Flux<Integer> loadBlockPlacement() {
        log.info("enter loadBlockPlacement loading");
        Set<String> newSet = Sets.newHashSet();
        return stringTemplate.opsForValue()
                .get(RedisKey.PLACEMENT_BLOCK.getPrefix())
                .switchIfEmpty(Mono.just(""))
                .flatMap(s -> {
                    if (StringUtils.isNotBlank(s)){
                        List<Long> ids = ObjectMapperUtils.fromJSONArray(s, Long.class);
                        log.info("loadBlockPlacement size {} info {}", ids.size(), s);
                        newSet.addAll(ids.stream().map(String::valueOf).collect(Collectors.toSet()));
                        blockCache = newSet;
                    }
                    return Mono.just(1);
                }).onErrorResume(throwable ->{
                    log.info("loadBlockPlacement error {}", throwable);
                    return Mono.just(1);
                }).flux();
    }

    public PlacementModel getPlacement(Long placementId) {

        PlacementModel placementModel = Optional.ofNullable(placeCache).orElse(new HashMap<>()).getOrDefault(placementId, null);
        return placementModel;
    }

    public boolean isBlockPlacement(long placementId) {
        return blockCache.contains(String.valueOf(placementId));
    }


    @Override
    public int size() {
        return Optional.ofNullable(placeCache).orElse(new HashMap<>()).size();
    }
}