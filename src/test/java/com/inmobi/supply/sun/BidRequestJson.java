package com.inmobi.supply.sun;

import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.utils.LogUtils;
import com.stars.bid.pb.BidRequest;
import lombok.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidRequestJson {

    /**
     * 媒体端请求id 选填
     */
    private String requestId;

    /**
     * 请求时间 非必填
     */
    private Long requestTime;

    /**
     * app信息 必填
     */
    private App app;

    /**
     * 广告位信息 必填
     */
    private Site site;

    /**
     * 竞价信息
     */
    private Imp imp;

    /**
     * 设备信息 必填
     */
    private Device device;

    /**
     * 用户信息 选填
     */
    private User user;

    /**
     * 媒体等待时间
     */
    private Integer tmax;
    
    //app信息 必填
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class App {

        /**
         * 必填
         */
        private String bundleId;
        /**
         * 选填
         */
        private String storeUrl;
        /**
         * 选填(request为媒体app名称，response中为下载类广告的五要素-app名称)
         */
        private String appName;
        /**
         * 选填
         */
        private String domain;

        // 下载广告五要素
        /**
         * // 下载广告五要素开发者名称(仅response中有效,request中不需要)
         */
        private String publisherName;
        /**
         * 下载广告五要素-版本(仅response中有效,request中不需要)
         */
        private String version;
        /**
         * 下载广告五要素-隐私链接(仅response中有效,request中不需要)
         */
        private String privacyLink;
        /**
         * 下载广告五要素-用户权限链接(仅response中有效,request中不需要)
         */
        private String permissionLink;
    }

    // 广告位信息 必填
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Site {
        /**
         * 广告位id
         */
        private String id;
        /**
         * 类型 1：开屏  2：信息流
         */
        private Integer adType;

        /**
         * 支持创意规格
         */
        private List<CreativeSpecs> creativeSpecs;
        /**
         * 支持的交互类型，默认全部支持
         */
        private List<InteractionType> supportInteractionType;
        /**
         * 是否支持302跳转，默认不支持
         */
        private Boolean support302;

    }

    //支持创意规格
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CreativeSpecs {
        /**
         * 支持的素材类型，默认全部支持
         */
        private CreativeType supportCreativeType;
        private Size supportSizes;
    }

    //支持的素材尺寸
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Size {
        /**
         * 宽
         */
        private Integer w;
        /**
         * 高
         */
        private Integer h;
    }

    //竞价信息
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Imp {
        /**
         * 交易模式为RTB时，必传。单位为cpm分
         */
        Float floorPrice;
        /**
         * 交易模式为PD时，必传 该值不为空时会忽略floor_price
         */
        private List<Pmp>  pmp;
        /**
         * 交易模式
         */
        private SettleType settleType;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        public static class Pmp {
            /**
             * 交易时间
             */
            private Long dealTime;
            private String dealId;
            /**
             * 线下deal价格，单位为cpm分
             */
            private Float dealPrice;
        }
    }

    //设备信息 必填
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Device {
        /**
         * 标准Ua 必填
         */
        private String ua;
        /**
         * 用户客户端ip，上传服务器IP将被反作弊过滤 必填
         */
        private String ipv4;
        /**
         * ipv6
         */
        private String ipv6;
        /**
         * ios idfa
         */
        private String idfa;
        /**
         * ios idfv
         */
        private String idfv;
        /**
         * ios idfa md5加密值   ios时idfa/idfa_md5必填一个
         */
        private String idfaMd5;
        /**
         * android imei
         */
        private String imei;
        /**
         * android imei md5加密值 android时imei/imei_md5/oaid/oaid_md5必填一个
         */
        private String imeiMd5;
        /**
         * android oaid明文
         */
        private String oaid;
        /**
         * android oaid md5加密值
         */
        private String oaidMd5;
        /**
         * android id
         */
        private String androidId;
        /**
         * android id md5加密值 android id/android_id_md5选填一个。推荐
         */
        private String androidIdMd5;
        /**
         * 设备类型 必填
         */
        private DeviceType deviceType;
        /**
         * 设备屏幕纵向分辨率 必填
         */
        private Integer h;
        /**
         * 设备屏幕横向分辨率 必填
         */
        private Integer w;
        /**
         * 操作系统 必填
         */
        private OsType os;
        /**
         * 操作系统版本 必填
         */
        private String osv;
        /**
         * 手机品牌，如iPhone 必填
         */
        private String make;
        /**
         * 手机型号，如HUAWEI，iphone11,4 必填
         */
        private String model;
        /**
         * 运营商 必填
         */
        private Carrier carrier;
        /**
         * 联网环境 必填
         */
        private ConnType connType;
        /**
         * 最近一次开机时间 选填  取原值进⾏传输iOS：1623815045.970028 Android：ec7f4f33-411a-47bc-8067-744a4e7e0723
         */
        private String bootSecond;
        /**
         * 最近更新操作系统的时间 选填  取原值进⾏传输iOS：1581141691.570419583 Android：1004697.709999999
         */
        private String updateSecond;
        /**
         * CPU频率
         */
        private Double cpuFrequency;
        /**
         * CPU核心数
         */
        private Integer cpuNumber;
        /**
         * 电池电量
         */
        private Integer batteryPower;
        /**
         * 电池充电状态
         */
        private String batteryStatus;
        /**
         * caid
         */
        private String caid;
        /**
         * caid版本
         */
        private String caidVersion;
        /**
         * 上一版本caid
         */
        private String preCaid;
        /**
         * 上一个caid版本
         */
        private String preCaidVersion;
        /**
         * 历史caid版本
         */
        private String oldCaidVersion;
        /**
         * 国家
         */
        private String countryCode;
        /**
         * 语言
         */
        private String language;
        /**
         * 设备名称
         */
        private String phoneNameMd5;
        /**
         * 物理内存容量
         */
        double memorySize;
        /**
         * 硬盘容量
         */
        double diskSize;
        /**
         * 设备model
         */
        private String modelCode;
        /**
         * 设备标识
         */
        private String deviceName;
        /**
         * 时区
         */
        private String timeZone;
        /**
         * 设备所在经纬度 选填
         */
        private Geo geo;
        /**
         * lmt  0:未确定  1:受限制  2:被拒绝  3:授权
         */
        private Integer lmt;
        /**
         * 设备初始化时间  取原值进⾏传输iOS：1581141691.570419583 Android：1004697.709999999
         */
        private String birthTime;
        /**
         * 系统启动标识，bootId， 每次开机都会产生一个唯一的 ID
         */
        private String bootMark;
        /**
         * 系统更新标识
         */
        private String updateMark;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Geo {
        private Float lat;
        private Float lon;
        /**
         * 获取来源   1: GPS 2:IP
         */
        private Integer source;
    }


    //用户信息 选填
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class User {
        /**
         * 性别  1：男   2：女
         */
        private Integer gender;
        /**
         * 年龄
         */
        private Integer age;
        /**
         * 用户关键信息
         */
        private List<String> keywords;
        /**
         * 媒体端，人群tag id
         */
        private List<String> tagId;
        /**
         * appList 信息 支持明文或base64方式传输
         */
        private AppList app_list;
        /**
         * 兴趣标签
         */
        private List<String>  userCategoryList;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AppList {
        /**
         * 示例：a,b,c  或对content原始内容转成byte再进行Base64
         */
        private String content;
        /**
         * 是否进行base64
         */
        private Boolean base64Flag;
    }

    enum SettleType {
        SETTLE_TYPE_UNKNOWN(0),
        RS(1),
        PD(2),
        PDB(3);

        @Getter
        private int code;

        SettleType(int code){
            this.code = code;
        }
    }

    enum CreativeType {
        CREATIVE_TYPE_UNKNOWN(0),
        IMAGE(1),
        VIDEO(2),
        GIF(3),
        MUTI_IMAGE(4),
        ;

        CreativeType(int code) {
            this.code = code;
        }

        @Getter
        private int code;
    }

    enum InteractionType {
        INTERACTION_TYPE_UNKNOWN(0),
        H5_LINK(1),
        DOWNLOAD(2),
        DEEPLINK(3),
        WECHAT_MINI_PROG(4),
        ;

        InteractionType(int code) {
            this.code = code;
        }

        @Getter
        private int code;
    }

    enum DeviceType {
        DEVICE_TYPE_UNKNOWN(0),
        PHONE(1),
        PAD(2);

        DeviceType(int code){
            this.code = code;
        }
        @Getter
        private int code;
    }

    enum OsType {
        OS_TYPE_UNKNOWN(0),
        IOS(1),
        ANDROID(2);
        OsType(int code){
            this.code = code;
        }
        @Getter
        private int code;
    }


    enum Carrier {
        CARRIER_UNKNOWN(0),
        MOBILE(1), //移动
        UNICOM(2), //联通
        TELECOM(3); //电信

        Carrier(int code){
            this.code = code;
        }
        @Getter
        private int code;
    }


    enum ConnType {
        CONN_TYPE_UNKNOWN(0),
        CONN_3G(1),
        CONN_4G(2),
        CONN_5G(3),
        WIFI(4),
        OTHER(99);

        ConnType(int code){
            this.code = code;
        }
        @Getter
        private int code;
    }

    public static void main(String[] args) throws InvalidProtocolBufferException {
        BidRequestJson bidRequestJson = new BidRequestJson();
        bidRequestJson.setRequestId("434343");
        bidRequestJson.setApp(App.builder().appName("erer").build());
        bidRequestJson.setImp(Imp.builder().settleType(SettleType.RS).build());
        String s = JSON.toJSONString(bidRequestJson);
        System.out.println(s);
        BidRequest.Builder bidRequestBuilder = BidRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(s, bidRequestBuilder);
        System.out.println(bidRequestBuilder);


    }

}


