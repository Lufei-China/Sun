package com.inmobi.supply.sun.repo.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.google.common.io.CharStreams;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author zeki.zhao
 */
@Slf4j
@Service
public class CnConfigRepo implements BaseRepo<Object> {
    private static final long OSS_LOAD_INTERVAL_SECONDS = 1200L;
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile String cnConfig;

    @Override
    public Flux<Object> loadRepo() {
        log.info("enter CnConfigRepo");
        return Flux.just(0).flatMap(x -> {
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            OSSObject ossObject = ossClient.getObject(ossConfig.getBucketName(), "supply/cnConfig/cnConfig.json");
            InputStream content = ossObject.getObjectContent();
            try {
                String temp = CharStreams.toString(new InputStreamReader(content));
                if (StringUtils.isNotBlank(temp)) {
                    cnConfig = temp;
                }
            } catch (IOException e) {
                log.error("load oss cnConfig error", e);
            }
            return Flux.just(0);
        });
    }

    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(OSS_LOAD_INTERVAL_SECONDS);
    }
}
