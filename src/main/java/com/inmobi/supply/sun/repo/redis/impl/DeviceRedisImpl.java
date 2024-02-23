package com.inmobi.supply.sun.repo.redis.impl;

import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.redis.BaseRedisRepository;
import com.inmobi.supply.sun.constant.RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
public class DeviceRedisImpl implements BaseRedisRepository<DeviceModel> {
    @Autowired
    private ReactiveRedisTemplate<String, DeviceModel> deviceTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Mono<Boolean> save(DeviceModel deviceModel) {
        return null;
    }

    @Override
    public Mono<Boolean> update(DeviceModel deviceModel) {
        return null;
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return null;
    }

    @Override
    public Mono<DeviceModel> findById(Object id) {
        return deviceTemplate.opsForValue()
                .get(RedisKey.DEVICE_INFO.of(id))
                .filter(deviceModel -> deviceModel != null && StringUtils.isNotEmpty(deviceModel.getExternalId()));
    }

    public Mono<DeviceModel> findByStringId(String externalId) {
        return stringTemplate.opsForValue()
                .get(RedisKey.DEVICE_INFO.of(externalId))
                .map(p -> ObjectMapperUtils.fromJSON(p, DeviceModel.class))
                .filter(deviceModel -> deviceModel != null && StringUtils.isNotEmpty(deviceModel.getExternalId()));
    }

    @Override
    public Flux<DeviceModel> findAll() {
        return stringTemplate.opsForSet().members(RedisKey.DEVICE_SET.getPrefix()).flatMap(this::findByStringId);
    }
}