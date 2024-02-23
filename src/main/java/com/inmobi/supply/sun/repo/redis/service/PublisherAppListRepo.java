package com.inmobi.supply.sun.repo.redis.service;


import com.alibaba.fastjson.JSON;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.model.appList.AppListInfo;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PublisherAppListRepo implements BaseRepo<Integer> {

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Getter
    private volatile Map<String, List<AppListInfo>> publisherAppListInfoMap = new HashMap<>();


    @Override
    public Flux<Integer> loadRepo() {
        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.PUBLISHER_APPLIST_INFO.getPrefix())
                .switchIfEmpty(Mono.just(""))
                .flatMap(listInfo -> {
                    if (StringUtils.isNotBlank(listInfo)) {
                        Map<String, List<AppListInfo>> publisherAppListInfoMapNew = new HashMap<>();
                        publisherAppListInfoMapNew = ObjectMapperUtils.fromJSON(listInfo, publisherAppListInfoMapNew.getClass());
                        Iterator<Map.Entry<String, List<AppListInfo>>> iterator = publisherAppListInfoMapNew.entrySet().iterator();
                        while (iterator.hasNext()){
                            Map.Entry<String, List<AppListInfo>> next = iterator.next();
                            publisherAppListInfoMap.put(next.getKey(),JSON.parseArray(JSON.toJSONString(next.getValue()),AppListInfo.class));
                        }
                    }
                    return Mono.just(0);
                }).onErrorResume(exception -> {
                    log.error("PublisherAppListRepo loadRepo error:" + exception.getMessage());
                    return Mono.just(0);
                }).flux();
    }
}