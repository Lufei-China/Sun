package com.inmobi.supply.sun.config.dynamic.autoConf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author remi.li
 */
@Data
@ConfigurationProperties(prefix = "apollo.bootstrap")
public class Namespace {
    String namespaces;
}