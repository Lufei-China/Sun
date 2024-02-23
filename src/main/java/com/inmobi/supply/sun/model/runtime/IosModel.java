package com.inmobi.supply.sun.model.runtime;

import lombok.Data;

/**
 * ios 设备型号类
 *
 * @author remi.li
 */
@Data
public class IosModel {
    private String modelName;
    private String model;
    private String vendor;
    private String density;
    private int width;
    private int height;
    private int deviceType;
    private String announcedDate;
}
