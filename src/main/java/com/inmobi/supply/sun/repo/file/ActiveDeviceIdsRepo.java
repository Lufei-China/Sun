package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 过去N天某个dsp的高出价活跃用户集合
 *
 * @author remi.li
 */
@Slf4j
@Service
public class ActiveDeviceIdsRepo implements BaseRepo<Object> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    /**
     * <channelId - activeIds>
     */
    @Getter
    private volatile Map<String, List<String>> activeIdfaMap;
    @Getter
    private volatile Map<String, List<String>> activeImeiMd5Map;
    @Getter
    private volatile Map<String, List<String>> activeImeiMap;
    @Getter
    private volatile Map<String, List<String>> activeOaidMap;
    @Getter
    private volatile Map<String, List<String>> activeAndroidIdMd5Map;
    @Getter
    private volatile Map<String, List<String>> activeAndroidIdSha1Map;

    @Override
    public Publisher<Object> loadRepo() {
        /*String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
        OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
        Map<String, String> metricsMap = new HashMap<>();
        metricsMap.put("activeIdfa", "predict-server/placementMetrics/activeIdfaSet.txt");
        metricsMap.put("activeImeiMd5", "predict-server/placementMetrics/activeImeiMd5Set.txt");
        metricsMap.put("activeImei", "predict-server/placementMetrics/activeImeiSet.txt");
        metricsMap.put("activeOaid", "predict-server/placementMetrics/activeOaidSet.txt");
        metricsMap.put("activeAndroidIdMd5", "predict-server/placementMetrics/activeAndroidIdMd5Set.txt");
        metricsMap.put("activeAndroidIdSha1", "predict-server/placementMetrics/activeAndroidIdSha1Set.txt");
        return Flux.fromIterable(metricsMap.entrySet()).flatMap(entry -> {
            String filePath = entry.getValue();
            OSSObject activeIdObject = ossClient.getObject(ossConfig.getBucketName(), filePath);
            InputStream activeIdInputStream = activeIdObject.getObjectContent();
            Map<String, List<String>> tempActiveMap = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(activeIdInputStream)))) {
                String line = br.readLine();
                tempActiveMap = ObjectMapperUtils.fromJSON(line, Map.class);
            }  catch (Exception e) {
                log.error("ActiveDeviceIdsRepo process error", e);
            }
            if (MapUtils.isNotEmpty(tempActiveMap)) {
                switch (entry.getKey()) {
                    case "activeIdfa":
                        activeIdfaMap = tempActiveMap;
                        break;
                    case "activeImeiMd5":
                        activeImeiMd5Map = tempActiveMap;
                        break;
                    case "activeImei":
                        activeImeiMap = tempActiveMap;
                        break;
                    case "activeOaid":
                        activeOaidMap = tempActiveMap;
                        break;
                    case "activeAndroidIdMd5":
                        activeAndroidIdMd5Map = tempActiveMap;
                        break;
                    case "activeAndroidIdSha1":
                        activeAndroidIdSha1Map = tempActiveMap;
                        break;
                }
            }
            return Flux.just(0);
        });*/
        return Flux.just(0);
    }
}