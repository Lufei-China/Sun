package com.inmobi.supply.sun.config.dynamic.autoConf;

import com.ctrip.framework.apollo.Config;
import com.google.common.base.Function;
import com.inmobi.supply.framework.common.utils.SpringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * DynamicConfig interface
 *
 * @author remi.li
 */
public interface DynamicConfig {
    DynamicConfigService configService = SpringUtils.getApplicationContext().getBean(DynamicConfigService.class);

    <T> T get();

    default <T> T get(String path, String namespace, T defaultValue) {
        Config config = configService.getConfig(namespace);
        return config.getProperty(path, parseValue(), defaultValue);
    }

    default <T> Function<String, T> parseValue() {
        return new Function<String, T>() {
            @Nullable
            @Override
            public T apply(@Nullable String s) {
                return null;
            }
        };
    }
}