package com.inmobi.supply.sun.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SdkParam {
    D_TEXTSIZE("d-textsize", "42.0", "字体大小"),
    FORMAT("format", "sdkJson", "请求ad格式（json、html）"),
    SDK_FLAVOR("sdk-flavor", "china", "sdk区域（是china还是row）"),
    TZ("tz", "28800000", "时区"),
    SKDM("skdm", "JUd3QJg0D6OJrEES72R_y0dHjdgl-dN95CzL2qVLJJbJbQlX7zwP57sqURBRHJ_jtsoMaMt0b7RydQlm8N8pnw==", "加密相关，由aes、iv、hmac混合而成"),
    SKDV("skdv", "1", "server返回的sdk版本"),
    SDK_COLLECTED("sdk-collected", "0", "sdk是否收集location信息 1收集0不收集"),
    U_APPBID("u-appbid", "com.test", "APP包名"),
    ADTYPE("adtype", "native", "广告类型"),
    CLIENT_REQUEST_ID("client-request-id", "e4cfd971-827b-4b98-80c2-1c72abefa698", "随机生成的请求id"),
    A_PARENTVIEWWIDTH("a-parentViewWidth", "360", "容器宽度\\ios中是整个屏幕的宽度"),
    M10N_CONTEXT("m10n_context", "M10N_CONTEXT_ACTIVITY", "上下文（是否是activity）"),
    D_NETTYPE_RAW("d-nettype-raw", "1", "网络类型,设备连接wifi就是\"wifi\"，无wifi有4G为\"CTRadioAccessTechnologyLTE\",无wifi无4G就没有这个字段"),
    LOC_GRANULARITY("loc-granularity", "none", "申请的定位权限类型"),
    D_BRAND_NAME("d-brand-name", "honor", "手机品牌"),
    MK_ADS("mk-ads", "1", "缓存ad数量"),
    U_APPSECURE("u-appsecure", "0", "是否有明文传输限制"),
    U_LATLONG_ACCU("u-latlong-accu", "0.0,0.0,0", ""),
    D_DENSITY_DEPENDENT_SCREEN_SIZE("d-density-dependent-screen-size", "1080x1792", "屏幕像素，如1080x1920"),
    U_LOCATION("u-location", "北京-北京-朝阳", "地理位置"),
    PRELOAD_REQUEST("preload-request", "1", "是否预加载（0，1）"),
    D_DEVICE_SCREEN_DENSITY("d-device-screen-density", "3.0", "屏幕密度"),
    MK_VERSION("mk-version", "pr-SAND-HTETD-20200330", "sdk版本号（T替换点，26个大写字母替代版本号）"),
    D_MANUFACTURER_NAME("d-manufacturer-name", "HUAWEI", "手机制造厂商"),
    U_APPDNM("u-appdnm", "TestSdk", "应用名"),
    U_APPVER("u-appver", "1.0", "开发者应用版本号"),
    U_GENDER("u-gender", "m", "用户性别"),
    D_MEDIA_VOLUME("d-media-volume", "0", "音量大小"),
    D_MODEL_NAME("d-model-name", "FRD-AL10", "手机型号"),
    TRACKERTYPE("trackerType", "url_ping", "跟踪类型，如url_ping"),
    U_ID_MAP("u-id-map", "{\"O1\",\"f810e15685e1a044cd7f85414782596fc270" +
            "cafa\",\"OAID\",\"cb7f7de6-46fd-ba42-fdcf-bfb72d5cf71a\",\"UM5\",\"d4ffad52c790113540ac44b9d399b" +
            "cb5\"}","手机设备信息(Android 有IMEI\\OAID\\MD5, ios 有IDA 和IDV)"),
    U_ID_KEY("u-id-key", "1046952140", ""),
    D_LOCALIZATION("d-localization", "zh_CN_#Hans", "地区"),
    D_ORIENTATION("d-orientation", "1", "是否是横竖屏"),
    LOC_CONSENT_STATUS("loc-consent-status", "denied", "定位权限是否允许"),
    D_DEVICE_SCREEN_SIZE("d-device-screen-size", "360X597", "屏幕尺寸"),
    A_PRODUCTVERSION("a-productVersion", "NS-1.0.0-20160411", "产品时间（固定值）"),
    IM_PLID("im-plid", "1571509744572", "广告位"),
    TS("ts", "1586487510122", "时间戳"),
    D_DEVICEMACHINEHW("d-devicemachinehw", "12,7", "ios字典"),
    D_OSV("d-osv", "13.5", "操作系统版本"),
    D_UA("user-agent", "user-agent", "操作系统版本"),
    WIFI_MAC("c-ap-bssid", "", "wifi mac"),
    U_TRACKING_STATUS("u-tracking-status", "0", "ios是否开启广告追踪"),
    U_ID_ADT("u-id-adt", "0", "ios14一下是否开启广告追踪"),
    OS_UPDATE_TIME("osUpdateTime", "0", "ios14一下是否开启广告追踪"),
    BOOT_TIME("bootTime", "0", "ios有效"),
    BOOT_ID("bootId", "0", "安卓有效"),
    HMS_CORE_VERSION("hms-core-version", "0", "鸿蒙系统中HMS Core版本号"),
    AG_VERSION("ag-version", "0", "鸿蒙系统中的AG版本号"),
    ;

    private String param;
    private String example;
    private String comment;

    public static boolean validTrackType(String trackType){
        if ("webview".equalsIgnoreCase(trackType) || "url_ping".equalsIgnoreCase(trackType)){
            return true;
        }
        return false;
    }

    public String of() {
        return param;
    }


}
