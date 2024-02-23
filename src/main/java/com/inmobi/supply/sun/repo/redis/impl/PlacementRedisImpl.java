package com.inmobi.supply.sun.repo.redis.impl;

import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.config.redis.BaseRedisRepository;
import com.inmobi.supply.sun.constant.RedisKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PlacementRedisImpl implements BaseRedisRepository<PlacementModel> {
    @Autowired
    private ReactiveRedisTemplate<String, PlacementModel> plmTemplate;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Mono<Boolean> save(PlacementModel placementModel) {
        return stringTemplate.opsForSet().add(RedisKey.PLACEMENT_INFO_SET.getPrefix(), String.valueOf(placementModel.getId()))
                .then(plmTemplate.opsForValue().set(RedisKey.PLACEMENT_INFO.of(placementModel.getId()), placementModel));
    }

    @Override
    public Mono<Boolean> update(PlacementModel placementModel) {
        return stringTemplate.opsForSet().add(RedisKey.PLACEMENT_INFO_SET.getPrefix(), String.valueOf(placementModel.getId()))
                .then(plmTemplate.opsForValue()
                        .set(RedisKey.PLACEMENT_INFO.of(placementModel.getId()), placementModel));
    }

    @Override
    public Mono<Boolean> delete(Object id) {
        return stringTemplate.opsForSet().remove(RedisKey.PLACEMENT_INFO_SET.getPrefix(), id.toString())
                .then(plmTemplate.opsForValue().delete(RedisKey.PLACEMENT_INFO.of(id)));
    }

    @Override
    public Mono<PlacementModel> findById(Object id) {
        return plmTemplate.opsForValue()
                .get(RedisKey.PLACEMENT_INFO.of(id))
                .filter(placementModel ->
                        placementModel != null && placementModel.getId() != null);
    }

    @Override
    public Flux<PlacementModel> findAll() {
        return stringTemplate.opsForSet()
                .members(RedisKey.PLACEMENT_INFO_SET.getPrefix())
                .flatMap(this::findById)
                .flatMap(placementModel -> {
                    return Flux.just(placementModel);
                });

    }


}