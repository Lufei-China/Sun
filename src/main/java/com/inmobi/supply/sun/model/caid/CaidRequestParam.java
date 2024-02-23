package com.inmobi.supply.sun.model.caid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author remi.li
 *
 * caid请求参数
 */
@Data
@AllArgsConstructor
public class CaidRequestParam {
    @JsonProperty("dev_id")
    String devId;

    @JsonProperty("encrypted_device_info")
    String encryptedDeviceInfo;
}
