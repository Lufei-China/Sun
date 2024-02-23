package com.inmobi.supply.sun.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdPoolConfig {
    private long timeout;
    private int concurrency;

    public static AdPoolConfig of(long timeout, int concurrency) {
        return new AdPoolConfig(timeout, concurrency);
    }
}