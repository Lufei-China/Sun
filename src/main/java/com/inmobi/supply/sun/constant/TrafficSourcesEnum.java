package com.inmobi.supply.sun.constant;

import lombok.Getter;

public enum TrafficSourcesEnum {

    DIRECT(1,"直客"),
    AGENCY(2,"代理"),
    ADX(3,"ADX");

    @Getter
    private int code;
    @Getter
    private String name;

    TrafficSourcesEnum(int code,String name)
    {
        this.code = code;
        this.name = name;
    }
}
