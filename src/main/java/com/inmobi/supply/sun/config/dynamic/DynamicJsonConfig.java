package com.inmobi.supply.sun.config.dynamic;

import com.google.common.base.Function;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.sun.config.*;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * apollo json dynamic config
 *
 * @author remi.li
 */
@SuppressWarnings("unchecked")
@Slf4j
public enum DynamicJsonConfig implements DynamicConfig {
    //ad pool config
    SATURN_AD_POOL("adpool.saturn", "sun.adpool", new AdPoolConfig(80, 500)),
    PERF_AD_POOL("adpool.perf", "sun.adpool", new AdPoolConfig(80, 500)),

    //kafka config

    //tracker config
    BEACON("event.type.bacon", "application", new TrackerUrlConfig("127.0.0.1", "beacon")),
    SECOND_BEACON("event.type.second.beacon", "application", new TrackerUrlConfig("127.0.0.1", "win_beacon")), //废弃
    CLICK("event.type.click", "application", new TrackerUrlConfig("127.0.0.1", "click")), //废弃
    WIN_NOTICE("event.type.wn", "application", new TrackerUrlConfig("winnotice.w.inmobi.cn", "beacon")),
    NEW_BEACON("event.type.new.beacon", "application", new TrackerUrlConfig("127.0.0.1", "new_beacon")),
    NEW_CLICK("event.type.new.click", "application", new TrackerUrlConfig("127.0.0.1", "new_click")),

    ENRICH_GETUI("enrich.getui", "application",
            new GeTuiEnrichConfig(true, true, "inmobi", "inmobi",
                    "http://api.dmp.getui.com/accesser/auth", "http://api.dmp.getui.com/user/tasklist",
                    25, 500, 20, 14400, 900)),

    SUN_ENTRANCE_RATE_LIMITER("sunEntrance.rateLimiter", "application", new RateLimiterConfig("sunEntrance", 3500)),
    SATURN_RATE_LIMITER("saturn.rateLimiter", "application", new RateLimiterConfig("saturn", 3500)),
    PERF_RATE_LIMITER("perf.rateLimiter", "application", new RateLimiterConfig("perf", 3500)),
    OSS_CONFIG("oss.config", "application", new OssConfig("", "", "", "")),
    GETUI_RATE_LIMITER("getui.rateLimiter", "application", new RateLimiterConfig("getui", 1000)),
    CAID_ENRICH_CONFIG("enrich.caid", "application", new CaidEnrichConfig(300)),
    CAID_RATE_LIMITER("caid.ratelimiter", "application", new RateLimiterConfig("caid", 1000)),

    AB_TEST_CONFIG("ab.test.cofig","abTest",new ABTestConfig());
    ;

    private final String path;

    private final String namespace;

    private final Object defaultValue;


    DynamicJsonConfig(@NonNull String path, @NonNull String namespace, @NonNull Object defaultValue) {
        this.path = path;
        this.namespace = namespace;
        this.defaultValue = defaultValue;
    }

    @NonNull
    @Override
    public <T> T get() {
        return (T) get(path, namespace, defaultValue);
    }

    @Override
    public <T> Function<String, T> parseValue() {
        return new Function<String, @Nullable T>() {
            @Nullable
            @Override
            public T apply(@Nullable String s) {
                try {
                    return (T) ObjectMapperUtils.fromJSON(s, defaultValue.getClass());
                } catch (Exception e) {
                    log.error("DynamicJsonConfig get error", e);
                    return (T) defaultValue;
                }
            }
        };
    }
}