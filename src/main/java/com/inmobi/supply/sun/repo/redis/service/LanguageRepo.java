package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.LanguageModel;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LanguageRepo implements BaseRepo<Integer> {
    private volatile Map<String, LanguageModel> languageCache;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Override
    @Deprecated
    public Mono<Integer> loadRepo() {
        log.info("enter LanguageRepo load");
        Map<String, LanguageModel> newLanguageCache = new HashMap<>();
        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.LANGUAGE_INFO.getPrefix())
                .switchIfEmpty(Mono.error(new Exception("no elements")))
                .flatMap(jsonValue -> {
                    List<LanguageModel> languageModels = ObjectMapperUtils.fromJSONArray(jsonValue, LanguageModel.class);
                    if (CollectionUtils.isNotEmpty(languageModels)) {
                        languageModels
                                .stream()
                                .filter(languageModel -> languageModel != null && !StringUtils.isEmpty(languageModel.getName()))
                                .collect(Collectors.toList())
                                .forEach(p -> newLanguageCache.put(p.getName(), p));
                    }
                    return Mono.just(0);
                }).then(Mono.just(0).flatMap(integer -> {
                    languageCache = newLanguageCache;
                    return Mono.just(0);
                })).onErrorResume(exception -> {
                    log.error("LanguageRepo loadRepo error:" + exception.getMessage());
                    return Mono.just(0);
                });
    }

    @Deprecated
    public LanguageModel getLanguage(String name) {
        return Optional.ofNullable(languageCache).orElse(new HashMap<>()).getOrDefault(name, null);
    }

    @Override
    public int size() {
        return Optional.ofNullable(languageCache).orElse(new HashMap<>()).size();
    }
}