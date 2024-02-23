package com.inmobi.supply.sun.repo.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.creativeAudit.CreativeAuditRes;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author zeki.zhao
 */
@Slf4j
@Service
public class CreativeAuditRepo implements BaseRepo<Object> {
    private static final long OSS_LOAD_INTERVAL_SECONDS = 1200L;

    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile Map<String, Map<String, String>> creativeAuditMap;

    @Override
    public Flux<Object> loadRepo() {
        log.info("enter CreativeAuditRepo");
        return Flux.just(0).flatMap(x -> {
//            OssConfig ossConfig = DynamicJsonConfig.OSS_CONFIG.get();
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            OSSObject ossObject = ossClient.getObject(ossConfig.getBucketName(), "supply/creativeAudit/creativeAuditRes.txt");
            InputStream content = ossObject.getObjectContent();
            if (null != content) {
                Map<String, Map<String, String>> creativeAuditHiddenMap = new HashMap<>();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                while (true) {
                    String line = null;
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {
                        log.error("read oss file error", e);
                    }
                    if (StringUtils.isBlank(line)) {
                        break;
                    }
                    CreativeAuditRes creativeAuditRes = ObjectMapperUtils.fromJSON(line, CreativeAuditRes.class);
                    String key = new StringBuilder(creativeAuditRes.getPublisherId())
                            .append("_")
                            .append(creativeAuditRes.getAdvertiserId())
                            .toString();

                    Map<String, String> creativeMappingMap = creativeAuditHiddenMap.get(key);
                    if (MapUtils.isEmpty(creativeMappingMap)) {
                        creativeMappingMap = new HashMap<>();
                    }
                    creativeMappingMap.put(creativeAuditRes.getCreativeId(), creativeAuditRes.getAdapterCreativeId());
                    creativeAuditHiddenMap.put(key, creativeMappingMap);
                }
                creativeAuditMap = creativeAuditHiddenMap;
                try {
                    content.close();
                } catch (IOException e) {
                    log.error("close oss inputStream error", e);
                }
            }
            return Flux.just(0);
        });
    }

    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(OSS_LOAD_INTERVAL_SECONDS);
    }

    @Override
    public int size() {
        return creativeAuditMap.size();
    }
}
