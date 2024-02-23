package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.AppInfoBaseModel;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.AppInfoBaseRepoImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

/**
 * @Description AppInfoBaseRepo
 * @Date 2022/9/9 10:24
 * @Author william.wang
 */

@Slf4j
@Repository
public class AppInfoBaseRepo implements BaseRepo<Integer> {

    @Autowired
    private AppInfoBaseRepoImpl appInfoBaseRepoImpl;

    private volatile Map<String, AppInfoBaseModel> appInfoBase;

    @Override
    public Flux<Integer> loadRepo() {
        log.info("enter AppInfoBaseRepo load");
        Map<String, AppInfoBaseModel> newAppCache = new HashMap<>();
        return appInfoBaseRepoImpl.findAll()
                .switchIfEmpty(Flux.error(new Exception("no elements")))
                .filter(appModel -> ObjectUtils.isNotEmpty(appModel.getType()))
                .flatMap(x -> {
                    String key = x.getType() + "_" ;
                    if (x.getType() == 0) {
                        key+=x.getIdentifier();
                    }
                    else {
                        key +=x.getPackageName();
                    }
                    key += "_" + x.getOs();
                    newAppCache.put(key, x);
                    return Flux.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    appInfoBase = newAppCache;
                    log.info("AppInfoBase size={} {}", appInfoBase.size(), appInfoBase);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    log.error("AppInfoBaseRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });
    }

    @Override
    public int size() {
        return Optional.ofNullable(appInfoBase).orElse(new HashMap<>()).size();
    }

    public AppInfoBaseModel getAppInfoBaseModelByPackageName(String packageName, Integer os) {
        return Optional.ofNullable(appInfoBase).orElse(new HashMap<>()).getOrDefault("1" + "_" + packageName+ "_" + os, null);
    }

    public AppInfoBaseModel getAppInfoBaseModelByPublisherId(String publisherId, Integer os) {
        return Optional.ofNullable(appInfoBase).orElse(new HashMap<>()).getOrDefault("0" + "_" + publisherId + "_" + os, null);
    }
}
