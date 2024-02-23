package com.inmobi.supply.sun.config.wurfl;

import cn.inmobi.pb.api.OsType;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
public class WurflConfig {

    @Bean(value = "generalWURFLEngine")
    public GeneralWURFLEngine generalWURFLEngine() {
        GeneralWURFLEngine capabilitiesEngine;
        String isLocal = System.getProperty("local");
        if ("true".equals(isLocal)) {
            capabilitiesEngine = new GeneralWURFLEngine("/Users/gavin.xu/Downloads/wurfl.zip");
        } else {
            capabilitiesEngine = new GeneralWURFLEngine("/opt/sun/wurfl.zip");
        }
        capabilitiesEngine.load();
        return capabilitiesEngine;
    }

    public static void main(String[] args) {
        String ua = "Mozilla/5.0(Linux;Android;LIO-AL00Build/HUAWEILIO-AL00;wv) AppleWebKit/537.36(KHTML,like Gecko) Version/4.0Chrome/78.0.3904.108MobileSafari/537.36";
        GeneralWURFLEngine capabilitiesEngine;
        capabilitiesEngine = new GeneralWURFLEngine("/Users/hardy.yang/inmobi/workSpaceIdea/inmobi-supply-sun/deploy/docker_prod/wurfl.zip");
        capabilitiesEngine.load();
        DeviceModel deviceModel;
        Device device = capabilitiesEngine.getDeviceForRequest(ua);
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
        System.out.println(JSON.toJSONString(deviceModel));
    }
}