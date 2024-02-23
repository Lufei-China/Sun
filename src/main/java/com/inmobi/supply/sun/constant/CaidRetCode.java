package com.inmobi.supply.sun.constant;

import lombok.Getter;

/**
 * @author remi.li
 *
 * caid请求接口返回码
 */
public enum CaidRetCode {
    SUCCESS("0", "成功"),
    UNKNOW_ERROR("-1", "未知错误"),
    REQUEST_FORMAT_ERROR("100000", "请求包格式错误"),
    DEVICE_INVALID("100001", "设备信息无效"),
    DEVICE_ID_INVALID("100002", "dev_id无效"),
    CURRENT_DEVICE_SET_AD_DROP_OUT("101000", "当前设备已经设置广告退出"),
    UNOBTAINED_KEY_FOR_DEV_ID("103000", "未获取到dev_id对应秘钥"),
    DECRYPTION_FAILED("103002", "解密失败"),
    CAID_GEN_FAILED("103200", "CAID生成失败"),

    ;

    CaidRetCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Getter
    private String code;
    @Getter
    private String description;
}
