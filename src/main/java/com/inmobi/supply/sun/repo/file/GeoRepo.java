package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv4;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv6;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * geoIpv4 / geoIpv6加载
 * @author remi.li
 *
 */
@Slf4j
@Service
public class GeoRepo implements BaseRepo<Object> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile List<GeoLatLonIpv4> geoLatLonIpv4List;

    @Getter
    private volatile List<GeoLatLonIpv6> geoLatLonIpv6List;

    @Override
    public Flux<Object> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            List<GeoLatLonIpv4> tempGeoLatLonIpv4List = new ArrayList<>();
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            OSSObject geoIpv4Object = ossClient.getObject(ossConfig.getBucketName(), "supply/geo/geo_ipv4.txt");
            InputStream ipv4In = geoIpv4Object.getObjectContent();
            try {
                BufferedReader ipv4Br = new BufferedReader(new BufferedReader(new InputStreamReader(ipv4In)));
                String Ipv4Line;
                while ((Ipv4Line = ipv4Br.readLine()) != null) {
                    GeoLatLonIpv4 geoLatLonIpv4 = ObjectMapperUtils.fromJSON(Ipv4Line, GeoLatLonIpv4.class);
                    if (geoLatLonIpv4 != null
                            && geoLatLonIpv4.getLatitude() != null
                            && geoLatLonIpv4.getLongitude() != null) {
                        tempGeoLatLonIpv4List.add(geoLatLonIpv4);
                    }
                }
                if (CollectionUtils.isNotEmpty(tempGeoLatLonIpv4List)) {
                    tempGeoLatLonIpv4List.sort(new GeoLatLonIpv4());
                }
                log.info("geoLatLon ipv4 size={}", tempGeoLatLonIpv4List.size());
                geoLatLonIpv4List = tempGeoLatLonIpv4List;

                List<GeoLatLonIpv6> temoGeoLatLonIpv6List = new ArrayList<>();
                OSSObject geoIpv6Object = ossClient.getObject(ossConfig.getBucketName(), "supply/geo/geo_ipv6.txt");
                InputStream ipv6In = geoIpv6Object.getObjectContent();
                BufferedReader ipv6Br = new BufferedReader(new BufferedReader(new InputStreamReader(ipv6In)));
                    String ipv6Line;
                    while ((ipv6Line = ipv6Br.readLine()) != null) {
                        GeoLatLonIpv6 geoLatLonIpv6 = ObjectMapperUtils.fromJSON(ipv6Line, GeoLatLonIpv6.class);
                        if (geoLatLonIpv6 != null
                                && geoLatLonIpv6.getLatitude() != null
                                && geoLatLonIpv6.getLongitude() != null) {
                            temoGeoLatLonIpv6List.add(geoLatLonIpv6);
                        }
                    }
                    if (CollectionUtils.isNotEmpty(geoLatLonIpv6List)) {
                        temoGeoLatLonIpv6List.sort(new GeoLatLonIpv6());
                    }
                    log.info("geoLatLon ipv6 size={}", temoGeoLatLonIpv6List.size());
                geoLatLonIpv6List = temoGeoLatLonIpv6List;
            }  catch (Exception e) {
                log.error("getGeoLatLon ipv4 load error", e);
            }
            return Flux.just(0);
        });
    }
}
