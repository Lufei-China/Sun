package com.inmobi.supply.sun.repo.file;


import cn.inmobi.pb.api.OsType;
import com.alibaba.fastjson.JSON;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.creativeAudit.CreativeAuditRes;
import com.inmobi.supply.sun.model.material.MaterialInfo;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 加载大智慧送审成功素材
 */
@Slf4j
@Service
public class MaterialInfoRepo  implements BaseRepo<Integer> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private volatile Map<String, List<MaterialInfo>> mapListMaterialInfo = new HashMap<>();

    public Flux<Integer> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            OSSObject ossObject = ossClient.getObject(ossConfig.getBucketName(), "supply/creativeAudit/daZhiHuiCreative.txt");
            InputStream content = ossObject.getObjectContent();
            if (null != content) {
                Map<String, Map<String, String>> creativeAuditHiddenMap = new HashMap<>();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                List<MaterialInfo> materialInfos = new ArrayList<>();
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
                    MaterialInfo materialInfo = ObjectMapperUtils.fromJSON(line, MaterialInfo.class);
                    log.info("materialInfoResult:{}", JSON.toJSONString(materialInfo));
                    materialInfos.add(materialInfo);
                }

                if(CollectionUtils.isNotEmpty(materialInfos)){
                    log.info("materialInfosResult:{}", JSON.toJSONString(materialInfos));
                    mapListMaterialInfo = materialInfos.stream().collect(Collectors.groupingBy(MaterialInfo::getCreativeKey));
                }
                try {
                    content.close();
                } catch (IOException e) {
                    log.error("close oss inputStream error", e);
                }
            }
            return Flux.just(0);
        });
    }
}