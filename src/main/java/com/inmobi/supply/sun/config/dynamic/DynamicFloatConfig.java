package com.inmobi.supply.sun.config.dynamic;

import com.ctrip.framework.apollo.util.function.Functions;
import com.google.common.base.Function;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;

/**
 * apollo float dynamic config
 * @author remi.li
 */
@SuppressWarnings("unchecked")
@Slf4j
public enum DynamicFloatConfig implements DynamicConfig {
    SPLASH_SIZE_TOLERATE("splash.size.tolerate", "application", 15.0F), //开屏尺寸容错度
    FEEL_SIZE_TOLERATE("splash.size.tolerate", "application", 20.0F),   //信息流尺寸容错度
    DYNAMIC_FLOOR_COEFFICIENT("dynamic.floor.coefficient", "application", 0.75F),
    NOVICE_EXCHANGE_RCOST("novice.exchange.rcost","sun.novice", 0.00476F),
    NOVICE_DEMAND_RCOST("novice.demand.rcost","sun.novice", 0.00476F),
    MIX_FLOW_BID_TIMES_PE("mix.flow.bid.times.pe","application", 1.2F),

    ;

    private final String path;
    private final String namespace;
    private final Float defaultValue;

    DynamicFloatConfig(@NonNull String path, @NonNull String namespace, Float defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.namespace = namespace;
    }

    @Override
    public Float get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public Function<String, @Nullable Float> parseValue() {
        return Functions.TO_FLOAT_FUNCTION;
    }
}