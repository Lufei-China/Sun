package com.inmobi.supply.sun.model.mixFlowRate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class H5Request {

    /**
     * 请求id
     */
    private String requestId;

    /**
     * 包名
     */
    private String pkg;

    /**
     * 广告位id
     */
    private String sltid;
    /**
     * 请求ip
     */
    private String ip;
    /**
     * 请求ua
     */
    private String ua;


}