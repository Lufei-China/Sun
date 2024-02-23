package com.inmobi.supply.sun.config.dynamic.autoConf;

import com.ctrip.framework.apollo.Config;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * @author remi.li
 */
@AllArgsConstructor
@Data
public class DynamicConfigService {
    private Map<String, Config> container;

    public Config getConfig(String namespace) {
        return container.get(namespace);
    }
}