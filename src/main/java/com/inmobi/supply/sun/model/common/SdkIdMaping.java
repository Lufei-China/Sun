package com.inmobi.supply.sun.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sdk 设备id值
 */

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SdkIdMaping {
    @JsonProperty("O1")
    private String O1;
    @JsonProperty("UM5")
    private String UM5;
    @JsonProperty("MD5_IMEI")
    private String MD5_IMEI;
    @JsonProperty("SHA1_IMEI")
    private String SHA1_IMEI;
    @JsonProperty("OAID")
    private String OAID;

    //@JsonProperty("IDF")
    //private String IDF;
    @JsonProperty("IDA")
    private String IDA;
    @JsonProperty("IDV")
    private String IDV;
}
