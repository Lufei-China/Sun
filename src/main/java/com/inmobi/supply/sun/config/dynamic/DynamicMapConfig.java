package com.inmobi.supply.sun.config.dynamic;

import com.google.common.base.Function;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * apollo map dynamic config
 *
 * @author remi.li
 */
@Slf4j
@SuppressWarnings("unchecked")
public enum DynamicMapConfig implements DynamicConfig {
    DEFAULT_DELTA_HOUR_OF_DAY("default.delta.hour.of.day", "application", new HashMap()),
    CSID_MAPPING("csid.mapping", "application", new HashMap()),
    NOVICE_DROP_HOUR_BP_RATE("novice.drop.bp.hour.rate", "sun.novice", new HashMap()),
    TACTICS_THRESHOLD("tactics.threshold", "application", new HashMap()),
    MARKET_PLACE_PLACEMENT("market.place.placement", "application", new HashMap()),
    ID_CONFIDENCE_PLACEMENT("id.confidence.placement", "abTest", new HashMap()),
    PLACEMENT_SPLIT_MAPPING("placement.split.mapping","application",new HashMap()),
    CREATIVE_REPEAT_MAPPING("creative.repeat.mapping","application",new HashMap()),

    ;
    private final String path;
    private final String namespace;
    private final Map<?, ?> defaultValue;

    DynamicMapConfig(@NonNull String path, @NonNull String namespace, Map<?, ?> defaultValue) {
        this.path = path;
        this.namespace = namespace;
        this.defaultValue = defaultValue;
    }

    @NonNull
    @Override
    public Map<?, ?> get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public Function<String, Map<?, ?>> parseValue() {
        return new Function<String, Map<?, ?>>() {
            @Nullable
            @Override
            public Map<?, ?> apply(@Nullable String s) {
                try {
                    return (Map<?, ?>) ObjectMapperUtils.fromJSON(s, Map.class);
                } catch (Exception e) {
                    log.error("DynamicMapBooleanConfig get error", e);
                    return defaultValue;
                }
            }
        };
    }
}
