package com.inmobi.supply.sun.constant;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

/**
 * API 接入协议枚举
 * @author remi.li
 */
@Getter
public enum ProtocolConstant {
    UNKNOW_PROTO(0),
    JSON(1),
    PROTO_BUF(2),
    ;

    private int value;
    ProtocolConstant(int value) {
        this.value = value;
    }

    public static ProtocolConstant getProtocolBuyContentType(MediaType mediaType) {
        if (StringUtils.lowerCase(mediaType.toString()).startsWith("application/json")) {
            return JSON;
        }
        if (StringUtils.lowerCase(mediaType.toString()).startsWith("application/x-protobuf")
                || StringUtils.lowerCase(mediaType.toString()).startsWith("application/octet-stream")) {
            return PROTO_BUF;
        }
        return UNKNOW_PROTO;
    }
}
