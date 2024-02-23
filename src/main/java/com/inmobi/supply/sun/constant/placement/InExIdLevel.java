package com.inmobi.supply.sun.constant.placement;

import lombok.Getter;

/**
 * @author remi.li
 */
@Getter
public enum InExIdLevel {
    NO_IN_EX(0),
    PLACEMENT_ID(1),
    APP_ID(2),
    PUB_ACCOUNT_ID(3),
    ;

    InExIdLevel(int value) {
        this.value = value;
    }

    private int value;

    public static InExIdLevel of(int value) {
        for (InExIdLevel status : InExIdLevel.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return NO_IN_EX;
    }
}