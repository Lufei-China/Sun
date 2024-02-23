package com.inmobi.supply.sun.model.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

/**
 * @author remi.li
 *
 * toutiao lost notice
 */
@Data
public class TouTiaoLostNotice {
    String name;
    @JsonProperty("request_id")
    String requestId;
    String rit;
    int code;
    String reason;
    int tmax = 200;
    String extra;
    String tag;

    @Getter
    public enum LostCode {
        TIMEOUT(1, "响应超时"),
        PROTOCAL_ERROR(2, "协议错误"),
        FILTER_BEFORE_AUCTION(3, "竞价前过滤"),
        AUCTION_FAIL(4, "竞价失败"),
        OTHER(5, "其他异常"),
        ;
        LostCode(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        private int code;
        private String desc;
    }
}