package com.inmobi.supply.sun.model.common;

import lombok.Data;

/**
 * 经纬度
 *
 * @author gavin.xu
 */
@Data
public class LatLong {
    /**
     * 经
     */
    private double lat;
    /**
     * 纬
     */
    private double lon;
    /**
     * 精度
     */
    private double scala;
    /**
     * 是否设置精度
     */
    private boolean scalaPresent;

    public LatLong(double lat, double lon, double scala) {
        if (scala > 0) {
            this.scalaPresent = true;
        }
        this.lat = lat;
        this.lon = lon;
        this.scala = scala;
    }

}
