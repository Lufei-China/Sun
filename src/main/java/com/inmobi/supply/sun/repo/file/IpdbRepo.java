package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ipip.ipdb.City;
import net.ipip.ipdb.District;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;

/**
 * ipdb加载
 *
 * @author remi.li
 */
@Slf4j
@Service
public class IpdbRepo implements BaseRepo<Object> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile City city;

    @Getter
    private volatile District district;

    @Override
    public Publisher<Object> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            try {
                String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
                OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
                OSSObject ipdbObject = ossClient.getObject(ossConfig.getBucketName(), "supply/geo/mydata4vipchina3_cn.ipdb");
                InputStream cityIn = ipdbObject.getObjectContent();
                city = new City(cityIn);

                OSSObject quxianObject = ossClient.getObject(ossConfig.getBucketName(), "supply/geo/quxian.ipdb");
                InputStream quxianIn = quxianObject.getObjectContent();
                district = new District(quxianIn);
            } catch (IOException e) {
                log.error("IpdbConfig error", e);
            }
            return Flux.just(0);
        });
    }
}
