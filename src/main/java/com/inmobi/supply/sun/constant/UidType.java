package com.inmobi.supply.sun.constant;

import lombok.Getter;

/**
 * china uid type enum
 *
 * @author remi.li
 */
@Getter
public enum UidType {
    IDFA("IDA", false),
    IDFV("IDV", false),
    OAID("OAID", true),
    MD5_IMEI("MD5_IMEI", false),
    SHA1_IMEI("SHA1_IMEI", false),
    ANDROID_ID_SHA1("O1", true),
    ANDROID_ID_MD5("UM5", true),
    IMUC__5("imuc__5", true),
    ;
    private String value;
    private boolean needXorDecrypted;

    UidType(String value, Boolean needXorDecrypted) {
        this.value = value;
        this.needXorDecrypted = needXorDecrypted;
    }
}
