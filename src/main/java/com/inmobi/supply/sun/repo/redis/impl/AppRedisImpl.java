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
public class AppRedisImpl implements BaseRedisRepository<AppModel> {

    @Autowired
    private ReactiveRedisTemplate<String, AppModel> appTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Mono<Boolean> save(AppModel appModel) {
        return stringTemplate.opsForSet().add(RedisKey.APP_SET.getPrefix(), String.valueOf(appModel.getAppId()))
                .then(appTemplate.opsForValue().set(RedisKey.APP_INFO.of(appModel.getAppId()) , appModel));
    }

    @Override
    public Mono<Boolean> update(AppModel appModel) {
        return stringTemplate.opsForSet().add(RedisKey.APP_SET.getPrefix(), String.valueOf(appModel.getAppId()))
                .then(appTemplate.opsForValue()
                        .set(RedisKey.APP_INFO.of(appModel.getAppId()), appModel));
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return stringTemplate.opsForSet().remove(RedisKey.APP_SET.getPrefix(), id.toString())
                .then(appTemplate.opsForValue().delete(RedisKey.APP_INFO.of(id)));
    }

    @Override
    public Mono<AppModel> findById(Object id) {
        return appTemplate.opsForValue()
                .get(RedisKey.APP_INFO.of(id))
                .filter(appModel ->
                        appModel != null && appModel.getAppId() != null);
    }

    @Override
    public Flux<AppModel> findAll() {
        return stringTemplate.opsForSet()
                .members(RedisKey.APP_SET.getPrefix())
                .flatMap(this::findById);
    }
}