package com.inmobi.supply.sun.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 限制广告追踪字段取值
 *
 * @author remi.li
 */
public enum LatType {
    NOT_DETERMINED(0),
    RESTRICTED(1),
    DENIED(2),
    AUTHORIZED(3),
    ;
    @Getter
    private int value;

    LatType(int value) {
        this.value = value;
    }
}
