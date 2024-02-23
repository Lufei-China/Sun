package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.mixFlowRate.MixFlowRateCreative;
import com.inmobi.supply.sun.model.sandbox.MockAd;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@SuppressWarnings("unchecked")
public class MixFlowRateRepo implements BaseRepo<Integer> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile Map<String, MixFlowRateCreative> mixFlowRateMap;

    @Override
    public int size() {
        return Optional.ofNullable(mixFlowRateMap).orElse(new HashMap<>()).size();
    }

    @Override
    public Flux<Integer> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            String filePath = "supply/mixFlowRate/mixFlowRateNew";
            OSSObject mixFlowRateObject = ossClient.getObject(ossConfig.getBucketName(), filePath);
            InputStream mixFlowRateInputStream = mixFlowRateObject.getObjectContent();
            Map<String, MixFlowRateCreative> mixFlowRateTempMockAdMap = new HashMap();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(mixFlowRateInputStream)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(StringUtils.trim(line));
                }
            } catch (Exception e) {
                log.info("MixFlowRateRepo load error", e);
            }
            Map<String, Map> dataMap = ObjectMapperUtils.fromJSON(sb.toString(), Map.class);
            dataMap.forEach((key, data) -> {
                MixFlowRateCreative mixFlowRateCreative = ObjectMapperUtils.fromJSON(ObjectMapperUtils.toJSON(data), MixFlowRateCreative.class);
                mixFlowRateTempMockAdMap.put(key, mixFlowRateCreative);
            });
            mixFlowRateMap = mixFlowRateTempMockAdMap;
            return Flux.just(0);
        }).onErrorResume(throwable -> {
            log.error("load MixFlowRateRepo error!", throwable);
            return Flux.just(0);
        });
    }

    /**
     * 任务延迟执行秒数
     *
     * @return
     */
    @Override
    public Duration delaySeconds() {
        int randSec = random.nextInt(50);
        return Duration.ofSeconds(DEFAULT_DELAY_SECONDS + randSec);
    }

    /**
     * 任务间隔执行秒数
     *
     * @return
     */
    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS*2);
    }
}
