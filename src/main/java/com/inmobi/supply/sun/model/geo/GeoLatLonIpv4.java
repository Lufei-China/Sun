package com.inmobi.supply.sun.model.geo;

import lombok.Data;

import java.util.Comparator;

/**
 * 经纬度ipv4 model
 *
 * @author remi.li
 */
@Data
public class GeoLatLonIpv4 implements Comparator<GeoLatLonIpv4> {
    private Integer cityCf;
    private Integer confidenceFactorByte;
    private Integer countryCf;
    private Integer cnountryId;
    private Double latitude;
    private Double longitude;
    private Long max;
    private Long min;
    private Integer networkType;
    private Integer stateCf;
    private Integer zipCf;

    @Override
    public int compare(GeoLatLonIpv4 o1, GeoLatLonIpv4 o2) {
        if (o1.getMin() < o2.getMin()) {
            return -1;
        } else {
            return 1;
        }
    }
}