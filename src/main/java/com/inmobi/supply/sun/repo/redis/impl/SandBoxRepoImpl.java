package com.inmobi.supply.sun.repo.redis.impl;

import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.model.redis.AppInfoBaseModel;
import com.inmobi.supply.component.model.redis.MaterialSandbox;
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

/**
 * @Description SandBoxRepoImpl
 * @Date 2022/10/24 10:57
 * @Author william.wang
 */
@Slf4j
@Repository
public class SandBoxRepoImpl implements BaseRedisRepository<MaterialSandbox> {

    @Autowired
    private ReactiveRedisTemplate<String, MaterialSandbox> materialSandboxTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Mono<Boolean> save(MaterialSandbox materialSandbox) {
        return null;
    }

    @Override
    public Mono<Boolean> update(MaterialSandbox materialSandbox) {
        return null;
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return null;
    }

    @Override
    public Mono<MaterialSandbox> findById(Object id) {
        log.info("findById key {}", RedisKey.APP_INFO_BASE.of(id));
        return materialSandboxTemplate.opsForValue()
                .get(RedisKey.MATERIAL_SANDBOX.of(id))
                .switchIfEmpty(Mono.error(new Exception("findById no elements")))
                .filter(materialSandbox -> materialSandbox != null
                        && materialSandbox.getStatus() == 1
                );
    }

    @Override
    public Flux<MaterialSandbox> findAll() {
        return stringTemplate.opsForValue()
                .get(RedisKey.MATERIAL_SANDBOX_SET.getPrefix())
                .flux()
                .flatMap(v -> {
                    List<String> list = JSON.parseArray(v, String.class);
                    return Flux.fromArray(list.toArray());
                })
                .flatMap(this::findById);
    }
}
