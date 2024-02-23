package com.inmobi.supply.sun.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zeki.zhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeTuiEnrichConfig {
    private boolean enableGeTui;
    private boolean enableCache;

    private String userCode;
    private String authCode;
    private String authUrl;
    private String taskUrl;

    private long timeout;
    private int concurrency;

    private int taskLimit;

    private long cacheForNullSec;
    private long cacheForValSec;
}
