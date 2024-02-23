package com.inmobi.supply.sun.constant;

import com.inmobi.supply.sun.constant.placement.InExLevel;

/**
 * perf:1 brand:2 rtbdAll:3 didiRtb:301 JdRtb:302 gdtRtb:303
 * @author remi.li
 */
public enum AdPoolForwardScaleConstant {
    UNKNOW("0"),
    PERF("1"),
    BRAND("2"),
    RTBD_ALL("3"),
    DIDI_RTB("301", "7b898b82c21341aab5aee47f8d7a1d42"),
    JD_RTB("302", "9a151336f867432199a9a329791686a0"),
    GDT_RTB("303", "75f70122644b4d9499801b3936b03dfb"),
    ;

    private String value;
    private String dspChannelId;

    AdPoolForwardScaleConstant(String value) {
        this.value = value;
    }

    AdPoolForwardScaleConstant(String value, String dspChannelId) {
        this.value = value;
        this.dspChannelId = dspChannelId;
    }

    public String getValue() {
        return value;
    }

    public String getDspChannelId() {
        return dspChannelId;
    }

    public static AdPoolForwardScaleConstant of(String value) {
        for (AdPoolForwardScaleConstant adPoolForwardScaleConstant : AdPoolForwardScaleConstant.values()) {
            if (adPoolForwardScaleConstant.value.equals(value)) {
                return adPoolForwardScaleConstant;
            }
        }
        return UNKNOW;
    }
}
