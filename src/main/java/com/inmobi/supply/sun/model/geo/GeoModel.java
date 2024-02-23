package com.inmobi.supply.sun.model.geo;

import cn.inmobi.pb.api.CityLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;

/**
 * caaip and ipip 在redis中的存储模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoModel implements Serializable, Comparator<GeoModel> {
    private static final long serialVersionUID = 1L;
    private Integer code;
    private String name;
    protected String ipRangeFrom;
    protected String ipRangeEnd;
    protected Long intRangeFrom;
    protected Long intRangeEnd;
    private CityLevel level;

    @Override
    public int compare(GeoModel o1, GeoModel o2) {
        if (o1.getIntRangeFrom() < o2.getIntRangeEnd()) {
            return -1;
        } else if (o1.getIntRangeFrom() > o2.getIntRangeEnd() ){
            return 1;
        } else {
            return 0;
        }
    }
}