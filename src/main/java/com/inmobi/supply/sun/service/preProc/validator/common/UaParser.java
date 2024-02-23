package com.inmobi.supply.sun.service.preProc.validator.common;

import cn.inmobi.pb.api.OsType;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.model.runtime.IosModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 功能概述
 * className:      UaParser
 * package:        com.inmobi.supply.sun.service.preProc.validator.common
 * author:         Gavin.Xu
 * date:           2022/3/23
 */
@Service
@Slf4j
public class UaParser {
    @Autowired
    private GeneralWURFLEngine generalWURFLEngine;

    @Autowired
    Map<String, IosModel> iosModelMap;

    /**
     * wurfl分析android准备率在90%左右
     * @param runTimeContext
     * @return
     */
    public DeviceModel getAndroidModel(RunTimeContext runTimeContext) {
        DeviceModel deviceModel = new DeviceModel();
        String userAgent = runTimeContext.getUserAgent();
        try {
            if (StringUtils.isNotBlank(userAgent)) {
                deviceModel = getDeviceModel(runTimeContext,userAgent);
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"getAndroidModel error {}", e);
        }
        LogUtils.logInfo(runTimeContext.getRequestId(),"Wurfl Android model {}", deviceModel);
        return deviceModel;
    }

    /**
     * wurfl分析ios时，只能拿到osv，品牌，model拿不到，需要用hardwareId（iphone11,3）来映射
     * @param runTimeContext
     * @param vendor
     * @param model
     * @return
     */
    public DeviceModel getIosModel(RunTimeContext runTimeContext, String vendor, String model) {
        DeviceModel deviceModel = new DeviceModel();
        String userAgent = runTimeContext.getUserAgent();
        try {
            if (StringUtils.isNotBlank(userAgent)) {
                deviceModel = getDeviceModel(runTimeContext,userAgent);
            }
            //包含hardwareId
            if (StringUtils.isNotBlank(model) && model.contains(",")){
                IosModel iosModel = iosModelMap.get(StringUtils.trim(model));
                if (iosModel != null){
                    deviceModel.setModelName(iosModel.getModelName());
                    deviceModel.setResolutionWidth(iosModel.getWidth());
                    deviceModel.setResolutionHeight(iosModel.getHeight());
                }
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"getIosModel error {}", e);
        }
        LogUtils.logInfo(runTimeContext.getRequestId(),"Wurfl IOS model {}", deviceModel);
        return deviceModel;
    }

    /**
     * 利用wurfl技术能力转化所需手机信息
     * @param runTimeContext
     * @param userAgent
     * @return
     */
    @NonNull
    private DeviceModel getDeviceModel(RunTimeContext runTimeContext, String userAgent) {
        DeviceModel deviceModel;Device device = generalWURFLEngine.getDeviceForRequest(userAgent);
        PerfUtils.perf("sun.wurfl.parse", Tag.of("type", "all"));
        if (device.getId().contains("generic")) {
            PerfUtils.perf("sun.wurfl.parse", Tag.of("type", "generic"));
        }
        deviceModel = new DeviceModel();
        deviceModel.setResolutionWidth(Integer.valueOf(device.getCapability("resolution_width")));
        deviceModel.setResolutionHeight(Integer.valueOf(device.getCapability("resolution_height")));
        deviceModel.setModelName(device.getCapability("model_name"));
        deviceModel.setManufacturerName(device.getCapability("brand_name"));
        deviceModel.setIsTablet(device.getCapabilityAsBool("is_tablet") ? 1 : 0);
        deviceModel.setIsSmartPhone(device.getCapabilityAsBool("is_phone") ? 1 : 0);
        deviceModel.setDensity(device.getCapability("density_class"));
        deviceModel.setOsVersion(device.getCapability("device_os_version"));
        deviceModel.setExternalId(device.getId());
        deviceModel.setIsWirelessDevice(device.getCapabilityAsBool("is_wireless_device") ? 1 : 0);
        deviceModel.setDisplayMarketingName(device.getCapability("marketing_name"));
        if (device.getCapabilityAsBool("is_ios")) {
            deviceModel.setDeviceOsId(AbstractHandset.IOS_ID);
        } else if (device.getCapabilityAsBool("is_android")) {
            deviceModel.setDeviceOsId(AbstractHandset.ANDROID_OS_ID);
        } else {
            deviceModel.setDeviceOsId(OsType.UNKNOW_OS_TYPE_VALUE);
        }
        return deviceModel;
    }




}
