package com.inmobi.supply.sun.constant;

import com.inmobi.supply.sun.constant.placement.InExIdLevel;
import lombok.Getter;

public enum DeviceIdType {
    UNKNOW_DEVICE_ID_TYPE(0),
    IDFA(1),
    IMEI_MD5(2),
    IMEI_SHA1(3),
    ANDROID_ID_MD5(4),
    ANDROID_ID_SHA1(5),
    OAID(6),
    ;

    @Getter
    private int value;

    DeviceIdType(int value) {
        this.value = value;
    }

    public static DeviceIdType of(int value) {
        for (DeviceIdType status : DeviceIdType.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOW_DEVICE_ID_TYPE;
    }
}