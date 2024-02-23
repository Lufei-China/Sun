package com.inmobi.supply.sun.model.common;

import lombok.Data;

/**
 * @author remi.li
 */
@Data
public class Version {
    int major;
    int minor;
    int micro;

    public Version(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }
}
