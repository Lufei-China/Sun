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
 * Map<String, Map<String, Boolean>
 *
 * @author remi.li
 */
@SuppressWarnings("unchecked")
@Slf4j
public enum DynamicMapBooleanConfig implements DynamicConfig {
    SDK_RAWPATH_ISUSED("sdk.rawpath.isUsed", "application", new HashMap<>()),
    API_RAWPATH_ISUSED("api.rawpath.isUsed", "application", new HashMap<>()),
    CUSTOM_API_ISUSED("customApi.isUsed", "application", new HashMap<>()),
    ;
    private final String path;
    private final String namespace;
    private final Map<String, Map<String, Boolean>> defaultValue;

    DynamicMapBooleanConfig(@NonNull String path, @NonNull String namespace, Map<String, Map<String, Boolean>> defaultValue) {
        this.path = path;
        this.namespace = namespace;
        this.defaultValue = defaultValue;
    }

    @NonNull
    @Override
    public Map<String, Map<String, Boolean>> get() {
        return get(path, namespace, defaultValue);
    }

    @Override
    public  Function<String, Map<String, Map<String, Boolean>>> parseValue() {
        return new Function<String, Map<String, Map<String, Boolean>>>() {
            @Nullable
            @Override
            public Map<String, Map<String, Boolean>> apply(@Nullable String s) {
                try {
                    return (Map<String, Map<String, Boolean>>) ObjectMapperUtils.fromJSON(s, Map.class);
                } catch (Exception e) {
                    log.error("DynamicMapBooleanConfig get error", e);
                    return defaultValue;
                }
            }
        };
    }
}