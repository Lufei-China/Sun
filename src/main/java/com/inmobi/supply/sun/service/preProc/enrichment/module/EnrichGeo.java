package com.inmobi.supply.sun.service.preProc.enrichment.module;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.*;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.Geo;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.component.model.redis.placement.PlacementShakeCityInfo;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.geo.CaaGeoModel;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv4;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv6;
import com.inmobi.supply.sun.model.geo.GeoModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.CaaInfoRepo;
import com.inmobi.supply.sun.repo.file.GeoRepo;
import com.inmobi.supply.sun.repo.file.IpdbRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.IpUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import net.ipip.ipdb.CityInfo;
import net.ipip.ipdb.DistrictInfo;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.TACTICS_THRESHOLD;
import static com.inmobi.supply.sun.constant.RuntimeConstant.MUNICIPALITY_DIRECTLY;

/**
 * @author remi.li
 * @Date 2020.7.24
 **/
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class EnrichGeo implements ModuleWorker<EnrichmentDataType> {
    @Autowired
    private GeoRepo geoRepo;

    @Autowired
    private CaaInfoRepo caaInfoRepo;

    @Autowired
    private IpdbRepo ipdbRepo;

    @Override
    public Mono<EnrichmentDataType> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"EnrichGeo");
        String ipV4 = runTimeContext.getUserIpV4();
        String ipV6 = runTimeContext.getUserIpV6();
        try {
            Geo.Builder geoBuilder = Geo.newBuilder();
            if (runTimeContext.getLatLong() != null) {
                double lon = runTimeContext.getLatLong().getLon();
                double lat = runTimeContext.getLatLong().getLat();
                if (lon != 0) {
                    geoBuilder.setLongitude(lon);
                }
                if (lat != 0) {
                    geoBuilder.setLatitude(lat);
                }
                if (geoBuilder.getLatitude() != 0 && geoBuilder.getLongitude() != 0) {
                    geoBuilder.setLatLonType(1);
                }
            }
            Map<String, Object> geoModels = new HashMap<>();

            geoModels.put("caaInfo", Optional.ofNullable(caaInfoRepo.getCaaGeoModelList()).orElse(new ArrayList<>()));
            geoModels.put("latLonIpv4", Optional.ofNullable(geoRepo.getGeoLatLonIpv4List()).orElse(new ArrayList<>()));
            geoModels.put("latLonIpv6", Optional.ofNullable(geoRepo.getGeoLatLonIpv6List()).orElse(new ArrayList<>()));
            if (ipdbRepo.getCity() != null) {
                geoModels.put("ipipCity", ipdbRepo.getCity());
            }
            return Flux.fromIterable(geoModels.entrySet())
                    //.parallel(geoModels.entrySet().size())
                    //.runOn(Schedulers.parallel())
                    .flatMap(entry -> {
                        switch (entry.getKey()) {
                            case "ipipCity":
                                try {
                                    CityInfo cityInfo = ipdbRepo.getCity().findInfo(ipV4, "CN");
                                    DistrictInfo districtInfo = ipdbRepo.getDistrict().findInfo(ipV4, "CN");
                                    if (cityInfo != null && MUNICIPALITY_DIRECTLY.contains(cityInfo.getCityName())) {
                                        //直辖市
                                        geoBuilder.setProvinceCode(StringUtils.isNotBlank(cityInfo.getChinaAdminCode()) ? cityInfo.getChinaAdminCode() : "");
                                        if (districtInfo != null) {
                                            geoBuilder.setDistrictCode(StringUtils.isNotBlank(districtInfo.getChinaAdminCode()) ? districtInfo.getChinaAdminCode() : "");
                                        }
                                    } else {
                                        if (cityInfo != null) {
                                            geoBuilder.setCityCode(StringUtils.isNotBlank(cityInfo.getChinaAdminCode()) ? cityInfo.getChinaAdminCode() : "");
                                        }
                                        if (districtInfo != null) {
                                            geoBuilder.setDistrictCode(StringUtils.isNotBlank(districtInfo.getChinaAdminCode()) ? districtInfo.getChinaAdminCode() : "");
                                        }
                                    }

                                    //补充运营商+网络情况
                                    Device device = runTimeContext.getDevice();
                                    boolean connectTypeInValid = device.getConnectType() == null
                                            || device.getConnectType() == ConnectionType.UNKNOW_CONNECTION_TYPE
                                            || device.getConnectType() == ConnectionType.CELL_2G
                                            || device.getConnectType() == ConnectionType.CELL_3G;

                                    boolean carrierIsEmpty = device.getCarrier() == null
                                            || device.getCarrier() == Carrier.UNKNOW_CARRIER;

                                    if (!connectTypeInValid) {
                                        device.setConnTypeSource(1);
                                    }
                                    if (!carrierIsEmpty) {
                                        device.setCarrierSource(1);
                                    }
                                    if (connectTypeInValid || carrierIsEmpty) {
                                        if (StringUtils.isNotBlank(ipV4) && cityInfo != null) {
                                            if (connectTypeInValid) {
                                                String baseStation = cityInfo.getBaseStation();
                                                if (baseStation.equals("基站")) {
                                                    device.setConnectType(ConnectionType.CELL_4G);
                                                    device.setConnTypeSource(2);
                                                } else if (baseStation.equals("WIFI")) {
                                                    device.setConnectType(ConnectionType.WIFI);
                                                    device.setConnTypeSource(2);
                                                }
                                            }
                                            if (carrierIsEmpty) {
                                                String carrier = cityInfo.getIspDomain();
                                                switch (carrier) {
                                                    case "移动": {
                                                        device.setCarrier(Carrier.MOBILE);
                                                        device.setCarrierSource(2);
                                                        break;
                                                    }
                                                    case "联通": {
                                                        device.setCarrier(Carrier.UNICOM);
                                                        device.setCarrierSource(2);
                                                        break;
                                                    }
                                                    case "电信": {
                                                        device.setCarrier(Carrier.TELECOM);
                                                        device.setCarrierSource(2);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("ipipCity error", e);
                                }
                                break;
                            case "latLonIpv4":
                            case "latLonIpv6":
                                if (geoBuilder.getLongitude() == 0 || geoBuilder.getLatitude() == 0) {
                                    if (StringUtils.isNotBlank(ipV4) && entry.getKey().equals("latLonIpv4")) {
                                        //ipv4
                                        long ipLong = IpUtils.ipv4ToLong(ipV4);
                                        if (geoBuilder.getLongitude() == 0 || geoBuilder.getLatitude() == 0) {
                                            GeoLatLonIpv4 geoLatLonIpv4 = IpUtils.searchLatLonIpv4((List) entry.getValue(), ipLong);
                                            if (geoLatLonIpv4 != null) {
                                                double lat = geoLatLonIpv4.getLatitude();
                                                double lon = geoLatLonIpv4.getLongitude();
                                                geoBuilder.setLatitude(lat);
                                                geoBuilder.setLongitude(lon);
                                                runTimeContext.setInmobiLatitude(lat);
                                                runTimeContext.setInmobiLongitude(lon);
                                            }
                                        }
                                        if (geoBuilder.getLatitude() != 0 && geoBuilder.getLongitude() != 0) {
                                            geoBuilder.setLatLonType(2);
                                        }
                                    }
                                    if (StringUtils.isNotBlank(ipV6) && entry.getKey().equals("latLonIpv6")) {
                                        //ipv6
                                        try {
                                            BigInteger ipLong = IpUtils.ipv6ToLong(ipV6);
                                            if (geoBuilder.getLongitude() == 0 || geoBuilder.getLatitude() == 0) {
                                                GeoLatLonIpv6 geoLatLonIpv6 = IpUtils.searchLatLonIpv6((List) entry.getValue(), ipLong);
                                                if (geoLatLonIpv6 != null) {
                                                    double lat = geoLatLonIpv6.getLatitude();
                                                    double lon = geoLatLonIpv6.getLongitude();
                                                    geoBuilder.setLatitude(lat);
                                                    geoBuilder.setLongitude(lon);
                                                    runTimeContext.setInmobiLatitude(lat);
                                                    runTimeContext.setInmobiLongitude(lon);
                                                }
                                            }
                                            if (geoBuilder.getLatitude() != 0 && geoBuilder.getLongitude() != 0) {
                                                //PerfUtils.perf("geoFromSunIpv6");
                                                geoBuilder.setLatLonType(2); }
                                        } catch (UnknownHostException e) {
                                            //do nothing
                                            log.error("searchLatLonIpv6 error", e);
                                        }
                                    }
                                }
                                break;
                            default:
                                if (StringUtils.isNotBlank(ipV4)) {
                                    long ipLong = IpUtils.ipv4ToLong(ipV4);
                                    GeoModel geoModel = IpUtils.search((List) entry.getValue(), ipLong);
                                    switch (entry.getKey()) {
                                        case "caaInfo":
                                            CaaGeoModel caaGeoModel = null;
                                            if (geoModel != null) {
                                                caaGeoModel = (CaaGeoModel) geoModel;
                                                geoBuilder.setCaaCityCode(Optional.ofNullable(caaGeoModel.getCaaCityCode()).orElse(""));
                                                geoBuilder.setCaaProvinceCode(Optional.ofNullable(caaGeoModel.getCaaProvinceCode()).orElse(""));
                                                geoBuilder.setCaaCityName(Optional.ofNullable(caaGeoModel.getCaaCityName()).orElse(""));
                                                if(StringUtils.isNotBlank(caaGeoModel.getCaaStateName()) && caaGeoModel.getCaaStateName().contains("中国")){
                                                    PerfUtils.perf("sun.caa.province");
                                                    geoBuilder.setCaaStateName(Optional.ofNullable(caaGeoModel.getCaaCityName()).orElse(""));
                                                }else{
                                                    geoBuilder.setCaaStateName(Optional.ofNullable(caaGeoModel.getCaaStateName()).orElse(""));
                                                }
                                            }
                                            //匹配摇一摇逻辑 仅针对开屏
                                            if(null != runTimeContext.getPlacementModel()
                                                    && null != runTimeContext.getPlacementModel().getShakeFlag()
                                                    && 1 == runTimeContext.getPlacementModel().getShakeFlag()){
                                                LogUtils.logInfo(runTimeContext.getRequestId(),"yaoyiyaoPlacement info {}",JSON.toJSONString(runTimeContext.getPlacementModel().getMapListPlacementShakeCityInfo()));
                                                yaoYiYao(runTimeContext,caaGeoModel);
                                            }

                                        default:
                                            break;
                                    }
                                }
                                break;
                        }
                        return Mono.just(geoBuilder);
                    })
                    //.sorted((o1, o2) -> 0)
                    .last()
                    .flatMap(builder -> {
                        Geo geo = builder.build();
                        runTimeContext.setGeo(geo);
                        return Mono.just(new EnrichmentDataType());
                    })
                    .name("enrichGeo").metrics()
                    .onErrorResume(throwable -> {
                        log.error("EnricherService doProcess error", throwable);
                        return Mono.just(new EnrichmentDataType());
                    });
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    //匹配摇一摇逻辑
    private void yaoYiYao(RunTimeContext runTimeContext,CaaGeoModel caaGeoModel){
        PlacementModel placementModel = runTimeContext.getPlacementModel();
        //没有解析到省市 且设定了不限地域的规则
        if(null == caaGeoModel){
            Map<String, List<PlacementShakeCityInfo>> mapListPlacementShakeCityInfo = placementModel.getMapListPlacementShakeCityInfo();
            if(MapUtils.isEmpty(mapListPlacementShakeCityInfo)){
                return;
            }
            Iterator<Map.Entry<String, List<PlacementShakeCityInfo>>> iterator = mapListPlacementShakeCityInfo.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<String, List<PlacementShakeCityInfo>> next = iterator.next();
                Optional<PlacementShakeCityInfo> first = next.getValue().stream().filter(placementShakeCityInfo -> placementShakeCityInfo.getLevel() == 3).findFirst();
                if(first.isPresent()){
                    //策略id
                    Integer tacticsId = first.get().getTacticsId();
                    Map<?, ?> map = TACTICS_THRESHOLD.get();
                    String threshold = (String) map.get(String.valueOf(tacticsId));
                    runTimeContext.setActionThreshold(threshold);
                    runTimeContext.setActionType(1);
                    LogUtils.logInfo(runTimeContext.getRequestId(),"yaoYiYaoResult threshold {}",threshold);
                    break;
                }
            }
            return;
        }

        String caaCityCode = caaGeoModel.getCaaCityCode();
        String caaProvinceCode = caaGeoModel.getCaaProvinceCode();
        if(null != placementModel && (StringUtils.isNotBlank(caaCityCode) || StringUtils.isNotBlank(caaProvinceCode))){
            Map<String, List<PlacementShakeCityInfo>> mapListPlacementShakeCityInfo = placementModel.getMapListPlacementShakeCityInfo();
            if(MapUtils.isNotEmpty(mapListPlacementShakeCityInfo)){
                Set<Map.Entry<String, List<PlacementShakeCityInfo>>> entries = mapListPlacementShakeCityInfo.entrySet();
                Iterator<Map.Entry<String, List<PlacementShakeCityInfo>>> iterator = entries.iterator();
                while (iterator.hasNext()){
                    Map.Entry<String, List<PlacementShakeCityInfo>> next = iterator.next();
                    List<PlacementShakeCityInfo> value = next.getValue();
                    Optional<PlacementShakeCityInfo> unlimited = value.stream().filter(placementShakeCityInfo -> placementShakeCityInfo.getLevel() == 3).findFirst();
                    if(unlimited.isPresent()){
                        Integer tacticsId = unlimited.get().getTacticsId(); //策略id
                        Map<?, ?> map = TACTICS_THRESHOLD.get();
                        String threshold = (String) map.get(String.valueOf(tacticsId));
                        runTimeContext.setActionThreshold(threshold);
                        runTimeContext.setActionType(1);
                        LogUtils.logInfo(runTimeContext.getRequestId(),"yaoYiYaoResult threshold {}",threshold);
                        break;
                    }

                    Optional<PlacementShakeCityInfo> first = value.stream().filter(placementShakeCityInfo -> {
                        //省份
                        if (placementShakeCityInfo.getLevel() == 1 && StringUtils.isNotBlank(caaProvinceCode)) {
                            if (placementShakeCityInfo.getCityCode().equals(Integer.valueOf(caaProvinceCode))) {
                                return true;
                            }
                        }
                        //城市
                        if (placementShakeCityInfo.getLevel() == 2 && StringUtils.isNotBlank(caaCityCode)) {
                            if (placementShakeCityInfo.getCityCode().equals(Integer.valueOf(caaCityCode))) {
                                return true;
                            }
                        }
                        return false;
                    }).findFirst();
                    if(first.isPresent()){
                        //策略id
                        Integer tacticsId = first.get().getTacticsId();
                        Map<?, ?> map = TACTICS_THRESHOLD.get();
                        String threshold = (String) map.get(String.valueOf(tacticsId));
                        runTimeContext.setActionThreshold(threshold);
                        runTimeContext.setActionType(1);
                        LogUtils.logInfo(runTimeContext.getRequestId(),"yaoYiYaoResult threshold {}",threshold);
                        break;
                    }
                }
            }
        }
    }
}