package com.inmobi.supply.sun.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zeki.zhao
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutsideServiceConfig {
    private String serviceName;
    private boolean enable;
    private String endpoint;
}
