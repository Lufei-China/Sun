package com.inmobi.supply.sun.service.preProc.validator.common;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import com.google.common.collect.Maps;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class AbstractHandset<T> {

    protected static final Pattern ANDROID_P = Pattern.compile(".*AND.*");
    protected static final Pattern IOS_P = Pattern.compile(".*IOS.*");
    protected static final String ANDROID = "ANDROID";
    protected static final String IOS = "IOS";
    protected static final Pattern IOS_VERSION = Pattern.compile("(.*\\s)([0-9]{1,2}_[0-9](_[0-9])?)(\\s.*)");
    protected static final Pattern ANDROID_VERSION = Pattern.compile("(.*\\s)(Android )([0-9]{1,2}(.[0-9]{1,2})?(.[0-9]{1,2})?)(;)(\\s.*)");


    public static final String H = "H";
    public static final String W = "W";
    protected static final char X = 'X';
    protected static final char x = 'x';

    public static final int IOS_ID = 5;
    public static final int ANDROID_OS_ID = 3;


    public abstract OsType getOsType(T request);

    public abstract boolean isSDK();

    protected abstract Map<String, Integer> getWH(T request);

    protected abstract String getModel(OsType osType, T request);

    protected abstract String getVendor(T request , OsType osType, Device.DeviceBuilder builder);

    protected abstract Float getDensity(T request);

    protected abstract DeviceType getDeviceType(T request, OsType osType);

    public abstract Integer getOrientation(T request);

    public abstract ConnectionType getConnectionType(T request,RunTimeContext runTimeContext);

    public abstract Carrier getCarrier(T request);

    public abstract String getUpdateMark(T request , OsType osType, DeviceModel builder);

    public abstract String getBootMark(T request , OsType osType, DeviceModel builder);

    public OsType getOsType(T request, DeviceModel deviceModel) {
        OsType osType = getOsType(request);
        if (osType == OsType.UNKNOW_OS_TYPE && deviceModel != null) {
            Integer osId = deviceModel.getDeviceOsId();
            if (osId == IOS_ID) {
                osType =  OsType.IOS;
            } else if (osId == ANDROID_OS_ID) {
                osType =  OsType.ANDROID;
            }
        }
        return osType;
    }

    public Map<String, Integer> getHW(T request, DeviceModel deviceModel,RunTimeContext runTimeContext) {
        Map<String, Integer> wh = getWH(request);
        if (MapUtils.isEmpty(wh) && deviceModel != null) {
            wh = Maps.newHashMap();
            wh.put(W, Optional.ofNullable(deviceModel.getResolutionWidth()).orElse(0));
            wh.put(H, Optional.ofNullable(deviceModel.getResolutionHeight()).orElse(0));
        }
        if(deviceModel != null){
            runTimeContext.setInmobiWidth(Optional.ofNullable(deviceModel.getResolutionWidth()).orElse(0));
            runTimeContext.setInmobiRealHeight(Optional.ofNullable(deviceModel.getResolutionHeight()).orElse(0));
        }
        if(MapUtils.isNotEmpty(wh)){
            runTimeContext.setOriginalWidth(wh.get(W));
            runTimeContext.setOriginalRealHeight(wh.get(H));
        }
        return wh;
    }

    public String getModel(OsType osType, T request, DeviceModel deviceModel,RunTimeContext runTimeContext) {
        String model = getModel(osType,request);
        runTimeContext.setOriginalModel(model);
        if (StringUtils.isBlank(model) && deviceModel != null) {
            model = deviceModel.getModelName();
        }
        if(null != deviceModel){
            runTimeContext.setInmobiModel(StringUtils.trim(StringUtils.lowerCase(deviceModel.getModelName())));
        }
        //转小写，去除空白字符
        model = StringUtils.trim(StringUtils.lowerCase(model));
        return model;
    }

    public String getVendor(T request, DeviceModel deviceModel, OsType osType, Device.DeviceBuilder builder,RunTimeContext runTimeContext) {
        String vendor = getVendor(request,osType, builder);
        runTimeContext.setOriginalVendor(vendor);
        if (StringUtils.isBlank(vendor) && deviceModel != null) {
            builder.vendoorSource(1);// ua 解析
            vendor = deviceModel.getManufacturerName();
        }
        if(null != deviceModel){
            runTimeContext.setInmobiVendor(StringUtils.trim(StringUtils.lowerCase(deviceModel.getManufacturerName())));
        }
        //转小写，去除空白字符
        vendor = StringUtils.trim(StringUtils.lowerCase(vendor));
        return vendor;
    }

    public Float getDensity(T request, DeviceModel deviceModel) {
        Float density = getDensity(request);
        if ((density == null || density == 0) && deviceModel != null) {
            try {
                density = Float.parseFloat(deviceModel.getDensity());
            } catch (Exception e) {
                //log.warn("getDensity error! density:{}", deviceModel.getDensity());
            }
        }
        return density == null ? 0f : density;
    }

    public DeviceType getDeviceType(T request, DeviceModel deviceModel, OsType osType) {
        DeviceType deviceType = getDeviceType(request, osType);
        if (deviceType == DeviceType.UNKNOW_DEVICE_TYPE && deviceModel != null) {
            if (deviceModel.getIsSmartPhone() == 1) {
                deviceType =  DeviceType.PHONE;
            }
            if (deviceModel.getIsTablet() == 1) {
                deviceType =  DeviceType.TABLET;
            }
        }
        return deviceType;
    }

    public abstract String getOsVersion(T request, OsType osType, DeviceModel deviceModel, long placementId);


    protected String parseUaOsVersion(String userAgent, OsType osType) {
        Matcher matcher = (osType == OsType.IOS) ? IOS_VERSION.matcher(userAgent) : ANDROID_VERSION.matcher(userAgent);
        if (matcher.matches()) {
            String group = (osType == OsType.IOS) ? matcher.group(2) : matcher.group(3);
            if (StringUtils.isNotBlank(group)) {
                if (group.contains("_")) {
                    return StringUtils.replace(group, "_", ".");
                }
                return group;
            }
        }
        return null;
    }

    public abstract String getPublisherVendor(T request);

    public abstract String getPublisherModel(T request);

    public static void main(String[] args) {
        String ua = "Mozilla/5.0 (Linux; Android 11; 2106118C Build/RKQ1.210503.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/92.0.4515.131 Mobile Safari/537.36";
        Matcher matcher =  ANDROID_VERSION.matcher(ua);
        if (matcher.matches()) {
            String group = matcher.group(3);
            if (StringUtils.isNotBlank(group)) {
                if (group.contains("_")) {
                    System.out.println(StringUtils.replace(group, "_", "."));
                }
                System.out.println(group);
            }
        }
    }
}