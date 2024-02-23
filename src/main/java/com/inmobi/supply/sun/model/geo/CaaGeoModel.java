package com.inmobi.supply.sun.model.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author remi.li
 * caa model
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaaGeoModel extends GeoModel {
    private String caaCityCode;
    private String caaProvinceCode;
    private String caaCityName; //caa城市名称
    private String caaStateName; //caa省名
}