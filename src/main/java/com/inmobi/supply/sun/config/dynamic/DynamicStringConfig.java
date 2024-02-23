package com.inmobi.supply.sun.config.dynamic;

import com.google.common.base.Function;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.annotation.Nonnull;

/**
 * apollo string dynamic config
 *
 * @author remi.li
 */
@Slf4j
@SuppressWarnings("unchecked")
public enum DynamicStringConfig implements DynamicConfig {
    DEFAULT_MIN_REV_SHARE("default.min.rev.share", "application", "0.65"),
    DEFAULT_MAX_REV_SHARE("default.max.rev.share", "application", "0.95"),
    DEFAULT_REV_SHARE("default.rev.share", "application", "0.57"),
    DEFAULT_M_REV_SHARE("default.m.rev.share", "application", "1.2"),
    DEFAULT_NOISE_LEVEL("default.noise.level", "application", "3"),
    DEFAULT_MAX_DELTA("default.max.delta", "application", "0.25"),
    CREATIVE_AUDIT_PUB_ADV_ID("creative.audit.publisher.advertise.id", "application", "{}"),

    GETUI_CONFIG("getui.config", "application", ""),
    BAIDU_ACCOUNT("baidu.account", "application", "3775726195e0540ecbe4a5bb821c9845"),
    USD_CNY_URL("usd.cny.url", "application", "http://webforex.hermes.hexun.com/forex/quotelist?code=FOREXUSDCNY&column=Code,Price"),
    USD_TO_CNY("usd.to.cny", "application", "6.6988"),
    MONITOR_EVENT_HOST("monitor.event.host", "application", "https://mt.inmobi.cn/landingURL"),
    ;
    private final String path;
    private final String namespace;
    private final String defaultValue;

    DynamicStringConfig(@Nonnull String path, @Nonnull String namespace, String defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.namespace = namespace;
    }

    @Override
    @Nonnull
    public String get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public Function<String, String> parseValue() {
        return new Function<String, String>() {
            @Nullable
            @Override
            public String apply(@Nullable String s) {
                if (StringUtils.isNotBlank(s)) {
                    return s;
                }
                return defaultValue;
            }
        };
    }
}