package com.inmobi.supply.sun.model.common;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.earth.entity.DeviceIdType;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * App参数对象
 *
 * @author gavin.xu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Device {

    /**
     * device数据来源
     */
    public enum SourceType {
        REQUEST,
        DB
    }

    private IdMapping id;

    private String userAgent; //手机 ua 必填

    private DeviceType deviceType; //设备类型 0 unknown 1 phone 2 tablet 必填

    private OsType osType; //操作系统 0 unknown 1 Android 2 Windows 必填

    private String osVersion; //操作系统版本

    private String osMajorVersion; //主流版本

    private String vendor; //厂商 如Apple HuaWei

    private SourceType vendorSourceType;

    private int vendoorSource;
    private String model; //设备型号 如Iphone5s Galaxy

    private SourceType modelSourceType;

    private ConnectionType connectType; //设备网路类型 0 unknown 1 Wifi 2 2G 3 3G 4 4G 5 5G

    private Carrier carrier;

    private int connTypeSource; // 网络连接类型的来源  1：客户端上传；2：IP库

    private int carrierSource;
    /**
     * 设备宽高
     */
    private DeviceSize deviceSize;

    private SourceType deviceSizeSourceType;

    private Float deviceDensity; //屏幕密度

    private SourceType densitySourceType;

    private Integer deviceOrientation; //设备屏幕方向 0 unknown 1垂直 2水平

    private String language; //语言

    private String bootMark; //android 可能有值

    private String updateMark; //语言
    CaidModel caidModel; //for ios14

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeviceSize {
        private int width;
        private int height;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdMapping {
        private String inmobi_id;      // Inmobi唯一用户ID
        private String idfa;           // IOS必填。IOS为IDFA（明文）。
        private String idfv;           // IOS必填。IOS为IDFV。
        private String imei;           // Android必填。设备IMEI（明文）
        private String ssid;           //选填。无线网SSID名称
        private String wifiMac;       //选填。WIFI路由器MAC地址
        private String dsid;           //选填。苹果账号（dsid）
        private String imsi;           //选填。IMSI（SIM卡串号）
        private String openUdid;      //选填。IOS软件生成替代UDID的标识
        private String imeiMd5;       //设备IMEI_MD5
        private String androidId;     //选填。安卓设备的Android_id
        private String uuid;          //选填。Android手机系统生成的设备ID
        private String oaid;          //可选。匿名设备标识符。如果
        private String o1;            //取Android ID的⼩写40位SHA1值传⼊
        private String um5;           //Android ID 的⼩写MD5 加密值，Android设备
        private String imeiSha1;      // 移动设备IMEI的SHA1值，Android设备
        private String deviceId;      //安卓操作系统8.0之前回传deviceId
        private String idfaMd5;       //idfaMd5
        private String oaidMd5;       //oaidMd5
        private String paid;       //paid
        private String mac;       //mac
        private String macMd5;       //macMd5

        public IdType getPriorityIosId(){
            if (StringUtils.isNotBlank(idfa)){
                return idType(idfa,DeviceIdType.IDFA);
            }
            if (StringUtils.isNotBlank(idfaMd5)){
                return idType(idfaMd5,DeviceIdType.IDFAMD5);
            }
            if (StringUtils.isNotBlank(idfv)){
                return idType(idfv,DeviceIdType.IDFV);
            }
            return null;
        }

        public IdType getPriorityAndId(){
            if (StringUtils.isNotBlank(imeiMd5)){
                return idType(imeiMd5,DeviceIdType.IMEI_MD5);
            }
            if (StringUtils.isNotBlank(imeiSha1)){
                return idType(imeiSha1,DeviceIdType.IMEI_SHA1);
            }
            if (StringUtils.isNotBlank(oaid)){
                return idType(oaid,DeviceIdType.OAID);
            }
            if (StringUtils.isNotBlank(oaidMd5)){
                return idType(oaidMd5,DeviceIdType.OAIDMD5);
            }
            if (StringUtils.isNotBlank(imei)){
                return idType(imei,DeviceIdType.IMEI);
            }
            if (StringUtils.isNotBlank(o1)){
                return idType(o1,DeviceIdType.O1);
            }
            if (StringUtils.isNotBlank(um5)){
                return idType(um5,DeviceIdType.UM5);
            }
            return null;
        }

        private IdType idType(String id,DeviceIdType deviceIdType){
            return IdType.builder().deviceId(id).deviceIdType(deviceIdType).build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdType{
        private String deviceId;
        private DeviceIdType deviceIdType;
    }
}
