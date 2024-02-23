package com.inmobi.supply.sun.service.preProc.validator.common;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.google.common.collect.Maps;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.IosModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import io.netty.util.internal.MathUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("api")
public class HandsetForApiV3 extends AbstractHandset<BaseRequest> {
    @Autowired
    private Map<String, IosModel> iosModelMap;

    @Override
    public OsType getOsType(BaseRequest request) {
        String os = request.getDevice().getOs();
        if (StringUtils.isNotBlank(os)) {
            if (ANDROID.equalsIgnoreCase(os)) {
                return OsType.ANDROID;
            }
            if (IOS.equalsIgnoreCase(os)) {
                return OsType.IOS;
            }
        }
        //发现有些开发者没传os字段，在这里做一个补充
        if (StringUtils.isNotBlank(request.getDevice().getUa()) && request.getDevice().getUa().toLowerCase().contains("iphone")){
            return OsType.IOS;
        }
        if (StringUtils.isNotBlank(request.getDevice().getUa()) && request.getDevice().getUa().toLowerCase().contains("android")){
            return OsType.ANDROID;
        }
        return OsType.UNKNOW_OS_TYPE;
    }

    @Override
    public boolean isSDK() {
        return false;
    }

    @Override
    protected Map<String, Integer> getWH(BaseRequest request) {
        Map<String, Integer> map;
        //TODO BY REMI
        cn.inmobi.pb.sun.rr.base.Device device = request.getDevice();
        if (device.getH() <= 0 || device.getW() <= 0) {
            return null;
        }
        map = Maps.newHashMap();
        map.put(W, device.getW());
        map.put(H, device.getH());
        return map;
    }

    @Override
    protected String getModel(OsType osType, BaseRequest request) {
        return request.getDevice().getModel();
    }

    @Override
    protected String getVendor(BaseRequest request, OsType osType, Device.DeviceBuilder builder) {
        builder.vendoorSource(2);
        if (osType == OsType.IOS) {
            /*String requestModel = request.getDevice().getModel();
            IosModel iosModel = iosModelMap.get(requestModel);
            if (iosModel != null && StringUtils.isNotBlank(iosModel.getVendor())){
                return iosModel.getVendor();
            }*/
            return "apple";
        }
        return request.getDevice().getMake();
    }

    @Override
    protected Float getDensity(BaseRequest request) {
        int ppi = request.getDevice().getPpi();
        try {
            return Float.parseFloat(String.valueOf(ppi));
        } catch (NumberFormatException e) {
            //log.debug("api getDensity meet NumberFormatException density:{}", ppi);
        }
        return null;
    }

    @Override
    protected DeviceType getDeviceType(BaseRequest request, OsType osType) {
        int type = request.getDevice().getType();
        switch (type) {
            case 1:
                return DeviceType.PHONE;
            case 2:
                return DeviceType.TABLET;
        }
        if (osType == OsType.IOS) {
            String requestModel = request.getDevice().getModel();
            IosModel iosModel = iosModelMap.get(requestModel);
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
    public Integer getOrientation(BaseRequest request) {
        if (request.getDevice().getExt() != null) {
            return request.getDevice().getExt().getOrientation();
        }
        return null;
    }

    @Override
    public ConnectionType getConnectionType(BaseRequest request, RunTimeContext runTimeContext) {
        int connectionType = request.getDevice().getConnectiontype();
        runTimeContext.setOriginalConnType(String.valueOf(connectionType));
        switch (connectionType) {
            case 1:
                //actually Ethernet, not support
                return ConnectionType.UNKNOW_CONNECTION_TYPE;
            case 3:
                return ConnectionType.UNKNOW_CONNECTION_TYPE;
            case 2:
                return ConnectionType.WIFI;
            case 4:
                return ConnectionType.CELL_2G;
            case 5:
                return ConnectionType.CELL_3G;
            case 6:
                return ConnectionType.CELL_4G;
            case 7:
                return ConnectionType.CELL_5G;
        }
        return ConnectionType.UNKNOW_CONNECTION_TYPE;
    }

    @Override
    public Carrier getCarrier(BaseRequest request) {
        String carrier = request.getDevice().getCarrier();
        if (StringUtils.isBlank(carrier)) {
            return Carrier.UNKNOW_CARRIER;
        }
        switch (carrier) {
            case "ChinaMobile":
                return Carrier.MOBILE;
            case "ChinaUnicom":
                return Carrier.UNICOM;
            case "ChinaTelecom":
                return Carrier.TELECOM;
        }
        return Carrier.UNKNOW_CARRIER;
    }

    @Override
    public String getUpdateMark(BaseRequest request, OsType osType, DeviceModel builder) {
        return request.getDevice().getUpdateMark();
    }

    @Override
    public String getBootMark(BaseRequest request, OsType osType, DeviceModel builder) {
        return request.getDevice().getBootMark();
    }
    @Override
    public String getOsVersion(BaseRequest request, OsType osType, DeviceModel deviceModel, long placementId) {
        String deviceModelOsVersion = "";
        String uaOsVersion = "";
        String userUpload = "";
        if (deviceModel != null && StringUtils.isNotBlank(deviceModel.getOsVersion()) && !deviceModel.getOsVersion().equals("1.0")) {
            deviceModelOsVersion = deviceModel.getOsVersion();
        }
        if (StringUtils.isNotBlank(request.getDevice().getUa())) {
            uaOsVersion = parseUaOsVersion(request.getDevice().getUa(), osType);
        }
        userUpload = getOsV(request);

        LogUtils.logInfo(request.getRequestId(),"deviceModelOsVersion {},userUpload {}",deviceModelOsVersion,userUpload);
        //wurfl解析与开发者上传osv 对比，返回较大的
        if(StringUtils.isNotBlank(deviceModelOsVersion) && StringUtils.isNotBlank(userUpload)
            && StringUtils.isNumeric(StringUtils.split(deviceModelOsVersion,".")[0]) && StringUtils.isNumeric(StringUtils.split(userUpload,".")[0])){
            if(Integer.parseInt(StringUtils.split(deviceModelOsVersion,".")[0]) > Integer.parseInt(StringUtils.split(userUpload,".")[0])){
                return deviceModelOsVersion;
            }else {
                return userUpload;
            }
        }
        if(StringUtils.isNotBlank(deviceModelOsVersion)){
            PerfUtils.perf("sun.osVersion.source", "source","deviceModel");
            return deviceModelOsVersion;
        }
        if(StringUtils.isNotBlank(uaOsVersion)){
            PerfUtils.perf("sun.osVersion.source", "source","ua");
            return uaOsVersion;
        }
        if(StringUtils.isNotBlank(userUpload)){
            PerfUtils.perf("sun.osVersion.source", "source","userUpload");
            return userUpload;
        }

        return null;
    }

    private String getOsV(BaseRequest request){
        if (StringUtils.isNotBlank(request.getDevice().getOsv())) {
            String osv = request.getDevice().getOsv();
            List<String> osvList = Arrays.asList(StringUtils.split(osv, "."));
            boolean validOsv = true;
            //检验开发者的osVersion是否合法
            if (CollectionUtils.isNotEmpty(osvList)) {
                for (String eachNum : osvList) {
                    if (!StringUtils.isNumeric(eachNum)) {
                        validOsv = false;
                        break;
                    }
                }
            }
            if (validOsv) {
                return request.getDevice().getOsv();
            }
        }
        return null;
    }

    @Override
    public String getPublisherVendor(BaseRequest request) {
        return request.getDevice().getMake();
    }

    @Override
    public String getPublisherModel(BaseRequest request) {
        return request.getDevice().getModel();
    }
}