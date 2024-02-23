package com.inmobi.supply.sun.config.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.ctrip.framework.apollo.ConfigService;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.config.dynamic.autoConf.Namespace;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zeki.zhao
 */

@Configuration
public class OssClientConfig {
    @Autowired
    Namespace getNamespace;
    @Bean(value = "ossClient", destroyMethod = "shutdown")
    public OSS ossClient() {
        List<String> namespaceList = Arrays.asList(StringUtils.split(getNamespace.getNamespaces(), ","));
        DynamicConfigService configService =  new DynamicConfigService(namespaceList.stream().collect(Collectors.toMap(name -> name,
                ConfigService::getConfig, (o1, o2)-> o1)));
        String ossConfigStr = configService.getConfig("application").getProperty("oss.config", "");
        OssConfig config = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
        return new OSSClientBuilder().build(
                config.getEndpoint(),
                config.getAccessKeyId(),
                config.getAccessKeySecret()
        );
    }
}