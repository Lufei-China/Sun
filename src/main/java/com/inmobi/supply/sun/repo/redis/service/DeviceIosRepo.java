package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.DeviceIosModel;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ios Repo
 *
 * @author gavin.xu
 */

@Service
@Slf4j
public class DeviceIosRepo implements BaseRepo<Integer> {
    private volatile Map<String, DeviceIosModel> deviceIosModelCache;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Override
    @Deprecated
    public Mono<Integer> loadRepo() {
        log.info("enter DeviceIosRepo load");
        Map<String, DeviceIosModel> newCache = new HashMap<>();
        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.DEVICE_IOS.getPrefix())
                .switchIfEmpty(Mono.error(new Exception("no elements")))
                .flatMap(jsonValue -> {
                            List<DeviceIosModel> models = ObjectMapperUtils.fromJSONArray(jsonValue, DeviceIosModel.class);
                            if (CollectionUtils.isNotEmpty(models)) {
                                models.stream()
                                        .filter(p -> p != null && !org.springframework.util.StringUtils.isEmpty(p.getModel()))
                                        .collect(Collectors.toList())
                                        .forEach(p -> newCache.put(p.getModel(), p));
                            }
                            return Mono.just(0);
                        }).then(Mono.just(0).flatMap(integer -> {
                    deviceIosModelCache = newCache;
                    return Mono.just(0);
                })).onErrorResume(exception -> {
                    log.error("DeviceIosRepo loadRepo error:" + exception.getMessage());
                    return Mono.just(0);
                });
    }

    public DeviceIosModel getIosModel(String name) {
        return Optional.ofNullable(deviceIosModelCache).orElse(new HashMap<>()).getOrDefault(name, null);
    }

    @Override
    public int size() {
        return Optional.ofNullable(deviceIosModelCache).orElse(new HashMap<>()).size();
    }
}