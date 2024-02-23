package com.inmobi.supply.sun.config.dynamic;

import com.ctrip.framework.apollo.util.function.Functions;
import com.google.common.base.Function;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * apollo boolean dynamic config
 *
 * @author remi.li
 */
@SuppressWarnings("unchecked")
public enum DynamicBooleanConfig implements DynamicConfig {
    SANDBOX_REQUEST_ID("sandbox.requestId", "application", false),
    EARTH_ENABLED("earth.enabled", "application", false),
    SANDBOX_REQUEST_ENABLE("sandbox.requestId", "application", false),
    DEBUG_RR_SWITCH("debug.rr", "application", false),
    TRUMP_SUPPORT("trump.support", "application", true),
    CAID_SUPPORT("caid.support", "application", false),
    DEBUG_BID("debug.bid", "application", false),
    MKT_FLAG("mkt.flag","application",false),

    MQ_SEND("mq.send", "application", false),
    VERSION_CLEAN_IOS_HIGH_SUPPORT("version.clean.ios.high.support", "application", false),
    VERSION_CLEAN_IOS_LOW_SUPPORT("version.clean.android.low.support", "application", false),
    VERSION_CLEAN_ANDROID_HIGH_SUPPORT("version.clean.android.high.support", "application", false),
    VERSION_CLEAN_ANDROID_LOW_SUPPORT("version.clean.android.low.support", "application", false),

    NOVICE_SERVICE_FLAG("novice.service.flag", "sun.novice", false),
    NOVICE_HOUR_WHITE_FLAG("novice.hour.white.flag", "sun.novice", true),
    NOVICE_SERVICE_NEWER_FLAG("novice.drop.newer.flag", "sun.novice", false),
    NOVICE_SERVICE_ZERO_NETREV_FLAG("novice.drop.zero.netrev.flag", "sun.novice", false),
    NOVICE_SERVICE_BP_FLAG("novice.drop.bp.flag", "sun.novice", false),
    NOVICE_SERVICE_BP_CITY_FLAG("novice.drop.bp.city.flag", "sun.novice", false),

    ;

    private final String path;
    private final String namespace;
    private final Boolean defaultValue;

    DynamicBooleanConfig(@NonNull String path, @NonNull String namespace, Boolean defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.namespace = namespace;
    }

    @NonNull
    @Override
    public Boolean get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public Function<String, @Nullable Boolean> parseValue() {
        return Functions.TO_BOOLEAN_FUNCTION;
    }
}
