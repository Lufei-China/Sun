package com.inmobi.supply.sun.constant;

import lombok.Getter;

/**
 * openModel enum
 *
 * @author remi.li
 */
@Getter
public enum OpenModel {
    DOWNLOAD(0),
    SKSTORE(1),
    EXTERNAL(2),
    H5(3),
    DEEPLINK(4),
    INAPP(5);

    private int value;

    OpenModel(int value) {
        this.value = value;
    }
}