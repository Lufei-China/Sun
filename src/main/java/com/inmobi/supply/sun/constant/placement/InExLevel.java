package com.inmobi.supply.sun.constant.placement;

/**
 * @author remi.li
 *
 * 0:adpool，1:advertiser，2:adGroup
 */
public enum InExLevel {
    AD_POOL(0),
    ADVERTISER(1),
    AD_GROUP(2),
    ;

    InExLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    private int value;

    public static InExLevel of(int value) {
        for (InExLevel status : InExLevel.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}
