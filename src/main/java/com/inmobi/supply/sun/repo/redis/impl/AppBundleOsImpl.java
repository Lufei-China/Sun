package com.inmobi.supply.sun.repo.redis.impl;

import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.sun.config.redis.BaseRedisRepository;
import com.inmobi.supply.sun.constant.RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Repository
public class AppBundleOsImpl implements BaseRedisRepository<AppModel> {

    @Autowired
    private ReactiveRedisTemplate<String, AppModel> appTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Mono<Boolean> save(AppModel appInfoBaseModel) {
        return null;
    }

    @Override
    public Mono<Boolean> update(AppModel appInfoBaseModel) {
        return null;
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return null;
    }

    @Override
    public Mono<AppModel> findById(Object id) {
        return appTemplate.opsForValue()
                .get(RedisKey.APP_DICT_BUNDLE_OS_INFO.of(id))
                .filter(appModel ->
                        appModel != null && appModel.getBundleId() != null && appModel.getPlatformId() != null);
    }

    @Override
    public Flux<AppModel> findAll() {
        return stringTemplate.opsForSet()
                .members(RedisKey.APP_DICT_BUNDLE_OS_SET.getPrefix())
                .flatMap(this::findById);
    }
}
