package com.inmobi.supply.sun.repo.redis.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CnConfigRedisRepo implements BaseRepo<Integer> {
    private static final long OSS_LOAD_INTERVAL_SECONDS = 300L;

    @Getter
    private volatile Map<String,JSONObject> cnConfigMap;

    @Getter
    private volatile JSONObject iosModuleMapping;

    @Getter
    private volatile JSONObject androidModuleMapping;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Override
    public Flux<Integer> loadRepo() {
        return stringTemplate.opsForValue()
                .get(RedisKey.CN_CONFIG_INFO.getPrefix())
                .switchIfEmpty(Mono.just(""))
                .flatMap(s -> {
                    if (StringUtils.isNotBlank(s)){
                        JSONObject jsonObject = JSONObject.parseObject(s);
                        LogUtils.logInfo("CnConfigRedisRepo infos:{}",jsonObject.toJSONString());
                        cnConfigMap = jsonObject.getObject("customApiIntegrations", new TypeReference<Map<String, JSONObject>>(){});
                        iosModuleMapping = jsonObject.getJSONObject("iosModuleMapping");
                        androidModuleMapping = jsonObject.getJSONObject("androidModuleMapping");
                    }
                    return Mono.just(1);
                }).onErrorResume(throwable ->{
                    log.info("CnConfigRedisRepo error {}", throwable);
                    return Mono.just(1);
                }).flux();
    }

    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(OSS_LOAD_INTERVAL_SECONDS);
    }
}
