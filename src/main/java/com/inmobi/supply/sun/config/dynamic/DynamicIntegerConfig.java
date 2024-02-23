package com.inmobi.supply.sun.config.dynamic;

import com.ctrip.framework.apollo.util.function.Functions;
import com.google.common.base.Function;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;


/**
 * apollo int dynamic config
 *
 * @author remi.li
 */
@SuppressWarnings("unchecked")
@Slf4j
public enum DynamicIntegerConfig implements DynamicConfig {
    CORE_POOL_SIZE("muiltiThread.corePoolSize", "sun.adpool", 800),
    DEFAULT_MARGIN_BAND_COUNT("default.margin.band.count", "application", 3),
    RATE_LIMITER("sun.rateLimiter", "application", 30),
    DYNAMIC_FLOOR_TRAFFIC_PROPORTION("dynamic.floor.traffic.proportion", "application", 20), //动态底价流量比例
    DYNAMIC_FLOOR_RAISE_PRICE_PROPORTION("dynamic.floor.raise.price.proportion", "application", 10), //动态底价价格抬高比例
    DYNAMIC_FLOOR_DECLINED_PRICE_PROPORTION("dynamic.floor.declined.price.proportion", "application", 20), //动态底价价格低于pe比例

    NO_VB_PROPORTION("no.vb.proportion", "application", 20),

    NOVICE_DROP_NEWER_RATE("novice.drop.newer.rate", "sun.novice", 20),
    NOVICE_DROP_NEWER2_RATE("novice.drop.newer2.rate", "sun.novice", 50),
    NOVICE_DROP_ZERO_NETREV_RATE("novice.drop.zero.netrev.rate", "sun.novice", 95),
    NOVICE_DROP_BP_RATE("novice.drop.bp.rate", "sun.novice", 90),
    NOVICE_DROP_BP_CITY_RATE("novice.drop.bp.city.rate", "sun.novice", 90),

    CPC_PRICE_UP("cpc.price.up", "application", 500),
    SATURN_BAIDU_AD_POOL("adpool.baidu.saturn", "sun.adpool", 200),
    SATURN_BAIDU_FEED_AD_POOL("adpool.baidu.feed.saturn", "sun.adpool", 200),

    LOG_SIMPLE_RATE("log.simple.rate", "application", 20),
    MIX_FLOW_START_HOUR("mix.flow.start.hour", "application", 8),
    MIX_FLOW_END_HOUR("mix.flow.end.hour", "application", 24),
    MIX_FLOW_BURN_LIMIT("mix.flow.burn.limit", "application", 150),
    MIX_FLOW_RATE_COUNT_LIMIT("mix.flow.rate.count.limit", "application", 1),
    MIX_FLOW_RATE_LOW_COUNT_LIMIT("mix.flow.rate.low.count.limit", "application", 1),
    MIX_FLOW_RATE_EVENT_COUNT_LIMIT("mix.flow.rate.event.count.limit", "application", 1),
    MIX_FLOW_RATE_EVENT_LOW_COUNT_LIMIT("mix.flow.rate.event.low.count.limit", "application", 1),
    REDIRECT_302_RATE("redirect.302.rete", "application", 1),
    IMPR_EXPIRATION_TIMESTAMP("impr_expiration_timestamp","application",5400),
    CLICK_EXPIRATION_TIMESTAMP("click_expiration_timestamp","application",7200),
    REQUEST_TIME_OUT_THRESHOLD("request_time_out_threshold","application",250),
    REQUEST_TIME_OUT_TAKE("request_time_out_take","application",20),
    IOS_VERSION_MIN("ios.version.min","application",0),
    IOS_VERSION_MAX("ios.version.max","application",99),
    ANDROID_VERSION_MIN("android.version.min","application",0),
    ANDROID_VERSION_MAX("android.version.max","application",99),
    ;
    private final String path;
    private final String namespace;
    private final Integer defaultValue;

    DynamicIntegerConfig(@NonNull String path, @NonNull String namespace, Integer defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.namespace = namespace;
    }

    @NonNull
    @Override
    public Integer get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public Function<String, @Nullable Integer> parseValue() {
        return Functions.TO_INT_FUNCTION;
    }
}