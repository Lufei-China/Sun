package com.inmobi.supply.sun.model.geo;

import lombok.Data;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * 经纬度ipv6 model
 *
 * @author remi.li
 */
@Data
public class GeoLatLonIpv6 implements Comparator<GeoLatLonIpv6> {
    private Integer cityCf;
    private Integer confidenceFactorByte;
    private Integer countryCf;
    private Integer cnountryId;
    private Double latitude;
    private Double longitude;
    private BigInteger max;
    private BigInteger min;
    private Integer networkType;
    private Integer stateCf;
    private Integer zipCf;

    @Override
    public int compare(GeoLatLonIpv6 o1, GeoLatLonIpv6 o2) {
        return o1.getMin().compareTo(o2.getMin());
    }
}
