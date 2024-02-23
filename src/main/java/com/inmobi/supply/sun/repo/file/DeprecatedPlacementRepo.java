package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@SuppressWarnings("unchecked")
public class DeprecatedPlacementRepo implements BaseRepo<Integer> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile List<Long> deprecatedPlacementIdList;

    @Override
    public int size() {
        return Optional.ofNullable(deprecatedPlacementIdList).orElse(new ArrayList<Long>()).size();
    }

    @Override
    public Flux<Integer> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            String filePath = "supply/deprecatedPlacementId/placementIds";
            OSSObject mockAdObject = ossClient.getObject(ossConfig.getBucketName(), filePath);
            InputStream mockAdInputStream = mockAdObject.getObjectContent();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(mockAdInputStream)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(StringUtils.trim(line));
                }
            }  catch (Exception e) {
                log.info("DeprecatedPlacementRepo load error", e);
            }
            deprecatedPlacementIdList = ObjectMapperUtils.fromJSONArray(sb.toString(), Long.class);
            log.info("deprecatedPlacementIdList={}", deprecatedPlacementIdList);
            return Flux.just(0);
        });
    }
}
