package com.inmobi.supply.sun.repo.redis.impl;

import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.model.redis.AppInfoBaseModel;
import com.inmobi.supply.component.model.redis.AppModel;
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

import java.util.List;
import java.util.Set;

/**
 * @Description AppInfoBaseRepoImpl
 * @Date 2022/9/9 10:41
 * @Author william.wang
 */

@Slf4j
@Repository
public class AppInfoBaseRepoImpl implements BaseRedisRepository<AppInfoBaseModel> {

    @Autowired
    private ReactiveRedisTemplate<String, AppInfoBaseModel> appInfoBaseModelTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;


    @Override
    public Mono<Boolean> save(AppInfoBaseModel appInfoBaseModel) {
        return null;
    }

    @Override
    public Mono<Boolean> update(AppInfoBaseModel appInfoBaseModel) {
        return null;
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return null;
    }

    @Override
    public Mono<AppInfoBaseModel> findById(Object id) {
        log.info("findById key {}", RedisKey.APP_INFO_BASE.of(id));
        return appInfoBaseModelTemplate.opsForValue()
                .get(RedisKey.APP_INFO_BASE.of(id))
                .switchIfEmpty(Mono.error(new Exception("findById no elements")))
                .filter(appModel -> appModel != null
                        && appModel.getType() != null
                        && StringUtils.isNotEmpty(appModel.getPackageName())
                        && (appModel.getType() != 0 || StringUtils.isNotEmpty(appModel.getIdentifier())));
    }

    @Override
    public Flux<AppInfoBaseModel> findAll() {
        return stringTemplate.opsForValue()
                .get(RedisKey.APP_INFO_BASE_SET.getPrefix())
                .flux()
                .flatMap(v -> {
                    List<String> list = JSON.parseArray(v, String.class);
                    return Flux.fromArray(list.toArray());
                })
                .flatMap(this::findById);
    }
}
