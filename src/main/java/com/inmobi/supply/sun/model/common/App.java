package com.inmobi.supply.sun.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * App参数对象
 *
 * @author gavin.xu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class App {
    private long appId;
    private String appBundle;
    private String appName;
    private Version version;
    /**
     * app类型 预留
     */
    private int appCateGory;
    /**
     * 付费app 预留
     */
    private boolean paidApp;

    @Data
    class Version{
        private int major;
        private int minor;
        private int micro;
    }
}
