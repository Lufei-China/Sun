package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.framework.common.utils.DebugUtils;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.log.RateLogger;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.AppRedisImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class AppRepo implements BaseRepo<Integer> {
    @Autowired
    private AppRedisImpl appRedisImpl;

    private volatile Map<String, AppModel> appCache;

    //this may cause serious bug!!!!
    @Override
    @Deprecated
    public Flux<Integer> loadRepo() {
        log.info("enter AppRepo load");
        Map<String, AppModel> newAppCache = new HashMap<>();
        return appRedisImpl.findAll()
                .switchIfEmpty(Flux.error(new Exception("no elements")))
                .filter(appModel -> StringUtils.isNotBlank(appModel.getAppId()))
                .flatMap(x ->{
                    newAppCache.put(x.getAppId(), x);
                    return Flux.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    appCache = newAppCache;
                    log.info("appCache size={}", appCache.size());
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    log.error("AppRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });
    }

    //plz fetch only once!
    public AppModel getAppModel(String appId) {
        /*if (DebugUtils.isDebugEnv()) {
            AppModel appModel = new AppModel();
            appModel.setAppId("5029702");
            appModel.setPubAccountId("4028cb8b2b17f7cb012b186611b20014");
            appModel.setAppName("非常三国-最新3D动作卡牌策略手游");
            appModel.setMarketId(2);
            appModel.setAppMarketUrl(" https://itunes.apple.com/cn/app/%E9%9D%9E%E5%B8%B8%E4%B8%89%E5%9B%BD-%E6%9C%80%E6%96%B03d%E5%8A%A8%E4%BD%9C%E5%8D%A1%E7%89%8C%E7%AD%96%E7%95%A5%E6%89%8B%E6%B8%B8/id1185808799?mt=8&uo=4");
            appModel.setIconUrl(null);
            appModel.setBundleId("cn.smallrecipeapp.lazy");
            appModel.setPrimaryIndustryId(null);
            appModel.setSecondaryIndustryId(null);
            appModel.setPlatformId(1);
            appModel.setCoppaCompliant(1);
            appModel.setIsTestApp(null);
            appModel.setIsPaidApp(0);
            return appModel;
        }*/
        return Optional.ofNullable(appCache).orElse(new HashMap<>()).getOrDefault(appId, null);
    }

    @Override
    public int size() {
        return Optional.ofNullable(appCache).orElse(new HashMap<>()).size();
    }
}