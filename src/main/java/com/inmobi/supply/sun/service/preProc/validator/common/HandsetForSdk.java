package com.inmobi.supply.sun.service.preProc.validator.common;

import java.util.Map;
import java.util.regex.Matcher;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import com.google.common.collect.Maps;
import com.inmobi.supply.component.model.redis.DeviceIosModel;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.constant.SdkParam;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.DeviceIosRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.inmobi.supply.sun.constant.SdkParam.*;

@Slf4j
@Service("sdk")
public class HandsetForSdk extends AbstractHandset<Map<String, String>> {

    private final static String ANDROID_WIFI = "1";
    private final static String IOS_WIFI = "wifi";
    @Autowired
    DeviceIosRepo deviceIosRepo;

    @Override
    public OsType getOsType(Map<String, String> request) {
        String mkVersion = request.get(MK_VERSION.getParam());
        if (StringUtils.isNotBlank(mkVersion)) {
            Matcher matcher = ANDROID_P.matcher(mkVersion);
            if (matcher.matches()) {
                return OsType.ANDROID;
            }
            matcher = IOS_P.matcher(mkVersion);
            if (matcher.matches()) {
                return OsType.IOS;
            }
        }
        return OsType.UNKNOW_OS_TYPE;
    }

    @Override
    public boolean isSDK() {
        return true;
    }

    @Override
    public Map<String, Integer> getWH(Map<String, String> request) {
        String sdkWh = request.get(D_DEVICE_SCREEN_SIZE.of());
        Map<String, Integer> wh;
        if (StringUtils.isNotBlank(sdkWh)) {
            String nomalWh = StringUtils.upperCase(sdkWh);
            String[] split = StringUtils.split(nomalWh, "X");
            if (ArrayUtils.isNotEmpty(split) && split.length == 2) {
                try {
                    int w = Integer.parseInt(split[0]);
                    int h = Integer.parseInt(split[1]);
                    if (w > 0 && h > 0) {
                        wh = Maps.newHashMap();
                        wh.put(W, w);
                        wh.put(H, h);
                        return wh;
                    }
                    //log.debug("getDeviceSizeFromRequest width or height number invalid, w:{},h:{}", w, h);
                } catch (NumberFormatException e) {
                    //log.warn("getDeviceSizeFromRequest meet NumberFormatException, sdkwh:{}", sdkWh);
                } catch (ArrayIndexOutOfBoundsException e) {
                    //log.warn("getDeviceSizeFromRequest meet ArrayIndexOutOfBoundsException, sdkwh:{}", sdkWh);
                }
            }
        }
        return null;
    }

    @Override
    public String getModel(OsType osType, Map<String, String> request) {
        if (osType == OsType.IOS){
            DeviceIosModel iosModel = deviceIosRepo.getIosModel(request.get(D_DEVICEMACHINEHW.getParam()));
            if (iosModel != null){
                return iosModel.getModel();
            }
        }
        return request.get(D_MODEL_NAME.of());
    }

    @Override
    public String getVendor(Map<String, String> request, OsType osType, Device.DeviceBuilder builder) {
        builder.vendoorSource(3);
        if (osType == OsType.IOS){
            DeviceIosModel iosModel = deviceIosRepo.getIosModel(request.get(D_DEVICEMACHINEHW.getParam()));
            if (iosModel != null){
                return iosModel.getVendor();
            }
        }
        return request.get(D_MANUFACTURER_NAME.of());
    }

    @Override
    public Float getDensity(Map<String, String> request) {
        String density = request.get(D_DEVICE_SCREEN_DENSITY.of());
        if (StringUtils.isNotBlank(density)) {
            try {
                String densityReplace = StringUtils.replace(density, SymbolString.COMMA, SymbolString.POINT);
                return Float.parseFloat(densityReplace);
            } catch (NumberFormatException e) {
                //log.warn("sdk getDensity meet NumberFormatException density:{}", density);
            }
        }
        return null;
    }



    @Override
    public DeviceType getDeviceType(Map<String, String> request, OsType osType) {
        //TODO BY REMI:UPDATE
        if (osType == OsType.IOS) {
            DeviceIosModel iosModel = deviceIosRepo.getIosModel(request.get(D_DEVICEMACHINEHW.getParam()));
            if (iosModel != null) {
                DeviceType deviceType = DeviceType.forNumber(iosModel.getDeviceType());
                if (deviceType != null){
                    return deviceType;
                }
            }
        }
        return DeviceType.PHONE;
    }


    @Override
    public Integer getOrientation(Map<String, String> request) {
        String deviceOrientation = request.get(D_ORIENTATION.of());
        if (StringUtils.isNotBlank(deviceOrientation)) {
            try {
                return Integer.parseInt(deviceOrientation);
            } catch (NumberFormatException e) {
                //log.warn("sdk getOrientation meet NumberFormatException deviceOrientation:{}", deviceOrientation);
            }
        }
        return null;
    }

    @Override
    public ConnectionType getConnectionType(Map<String, String> request, RunTimeContext runTimeContext) {
        String netType = request.get(D_NETTYPE_RAW.of());
        if (StringUtils.isNotBlank(netType)) {
            // android 1是wifi ios wifi传的是wifi，其他传的carrier
            if (ANDROID_WIFI.equals(netType) || IOS_WIFI.equalsIgnoreCase(netType)) {
                runTimeContext.setOriginalConnType(String.valueOf(ConnectionType.WIFI.getNumber()));
                return ConnectionType.WIFI;
            }
            // 传参判断不了，默认返回4g
            runTimeContext.setOriginalConnType(String.valueOf(ConnectionType.CELL_4G.getNumber()));
            return ConnectionType.CELL_4G;
        }
        runTimeContext.setOriginalConnType(String.valueOf(ConnectionType.UNKNOW_CONNECTION_TYPE.getNumber()));
        return ConnectionType.UNKNOW_CONNECTION_TYPE;
    }

    @Override
    public Carrier getCarrier(Map<String, String> request) {
        //TODO BY REMI
        return Carrier.UNKNOW_CARRIER;
    }

    @Override
    public String getUpdateMark(Map<String, String> request, OsType osType, DeviceModel builder) {
        return request.get(OS_UPDATE_TIME.of());
    }

    @Override
    public String getBootMark(Map<String, String> request, OsType osType, DeviceModel builder) {
        if (osType == OsType.IOS) {
            return request.get(BOOT_TIME.getParam());
        }
        return request.get(BOOT_ID.getParam());
    }
    @Override
    public String getOsVersion(Map<String, String> request, OsType osType, DeviceModel deviceModel, long placementId) {
        String version = "";
        String sdkOsVersion = request.get(D_OSV.of());
        if (StringUtils.isNotBlank(sdkOsVersion)) {
            version = sdkOsVersion;
        }
        String userAgent = request.get(SdkParam.D_UA.of());
        if (StringUtils.isBlank(userAgent)) {
            PerfUtils.perf("userAgentEmpty", Tag.of("requestType", "sdk"), Tag.of("osType", osType.name()));
        }
        if (StringUtils.isNotBlank(userAgent)) {
            String osVersion = parseUaOsVersion(userAgent, osType);
            if (StringUtils.isNotBlank(osVersion)) {
                version = osVersion;
            } else {
                log.info("osVersionEmpty, sdk ios ua={}", userAgent);
            }
        }
        if (StringUtils.isBlank(version)
                && deviceModel != null && !deviceModel.getOsVersion().equals("1.0")) {
            return deviceModel.getOsVersion();
        }
        return version;
    }

    @Override
    public String getPublisherVendor(Map<String, String> request) {
        return request.get(D_MANUFACTURER_NAME.of());
    }

    @Override
    public String getPublisherModel(Map<String, String> request) {
        return request.get(D_MODEL_NAME.of());
    }
}
