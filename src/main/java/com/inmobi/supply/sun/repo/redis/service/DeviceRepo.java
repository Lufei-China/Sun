package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.framework.common.utils.DebugUtils;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.log.RateLogger;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.DeviceRedisImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * userAgent Repo
 *
 * @author gavin.xu
 */

@Service
@Slf4j
public class DeviceRepo implements BaseRepo<Integer> {
    @Autowired
    private DeviceRedisImpl deviceRedis;

    private volatile Map<String, DeviceModel> DeviceCache;

    @Override
    @Deprecated
    public Flux<Integer> loadRepo() {
        log.info("enter DeviceRepo load");
        Map<String, DeviceModel> newDeviceCache = new HashMap<>();
        return deviceRedis.findAll()
                .switchIfEmpty(Flux.error(new Exception("no elements")))
                .filter(deviceModel -> StringUtils.isNotBlank(deviceModel.getExternalId()))
                .flatMap(x ->{
                    newDeviceCache.put(x.getExternalId(), x);
                    RateLogger.rateLogger(log).info("deviceId={} deviceJson={}", x.getExternalId(), ObjectMapperUtils.toJSON(x));
                    return Flux.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    DeviceCache = newDeviceCache;
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    log.error("DeviceRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });
    }

    public DeviceModel getDevice(String name) {
        if (DebugUtils.isDebugEnv()) {
            DeviceModel deviceModel = new DeviceModel();
            deviceModel.setExternalId("zyrex_za977_ver1");
            deviceModel.setManufacturerName("Zyrex");
            deviceModel.setResolutionWidth(480);
            deviceModel.setResolutionHeight(800);
            deviceModel.setDensity("1");
            deviceModel.setModelName("ZA977");
            deviceModel.setDeviceOsId(3);
            deviceModel.setDeviceTypeId(1);
            deviceModel.setOsVersionId(260);
            deviceModel.setOsMajorVersion("4.4");
            deviceModel.setOsVersion("4.4.2");
            deviceModel.setIsSmartPhone(1);
            deviceModel.setIsWirelessDevice(1);
            deviceModel.setIsTablet(0);
            deviceModel.setIsRtaHandset(1);
            return deviceModel;
        }
        return Optional.ofNullable(DeviceCache).orElse(new HashMap<>()).getOrDefault(name, null);
    }

    @Override
    public int size() {
        return Optional.ofNullable(DeviceCache).orElse(new HashMap<>()).size();
    }
}