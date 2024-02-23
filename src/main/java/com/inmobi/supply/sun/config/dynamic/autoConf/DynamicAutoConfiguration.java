package com.inmobi.supply.sun.config.dynamic.autoConf;

import com.ctrip.framework.apollo.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author remi.li
 */
@Configuration
@Slf4j
public class DynamicAutoConfiguration {
    @Bean
    public DynamicConfigService container(Namespace namespace) {
        List<String> namespaceList = Arrays.asList(StringUtils.split(namespace.namespaces, ","));
        return new DynamicConfigService(namespaceList.stream().collect(Collectors.toMap(name -> name,
                ConfigService::getConfig, (o1, o2)-> o1)));
    }

    @Bean
    Namespace getNamespace() {
        return new Namespace();
    }
}