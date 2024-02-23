package com.inmobi.supply.sun;

import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Optional;

/**
 * 功能概述
 * className:      WurflTest
 * package:        com.inmobi.supply.sun
 * author:         Gavin.Xu
 * date:           2022/1/25
 */
public class WurflTest {
    GeneralWURFLEngine capabilitiesEngine;

    @Before
    public void init(){
        long l = System.currentTimeMillis();
        //signal团队提供xml
        capabilitiesEngine = new GeneralWURFLEngine("/Users/gavin.xu/Downloads/wurfl.zip");
        //apsar提供的
        //capabilitiesEngine = new GeneralWURFLEngine("/Users/gavin.xu/Downloads/wurfl.xml");
        capabilitiesEngine.load();
        System.out.println(System.currentTimeMillis() - l);
    }

    @Data
    private static class DeviceDetection {
        private String ua;
        private String osv;
        private String brand;
        private String model;
        private String modelFullName;
        private String width;
        private String height;
        private float density;
        private DeviceType deviceType;
        private OsType osType;
    }


    @Test
    public void test(){
        String ua1 = "Mozilla/5.0 (Linux; Android 11; SM-A426U1 Build/RP1A.200720.012; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/94.0.4606.85 Mobile Safari/537.36";

        GeneralWURFLEngine capabilitiesEngine = new GeneralWURFLEngine("/Users/gavin.xu/Downloads/wurfl.zip");
        capabilitiesEngine.load();
        long l = System.currentTimeMillis();
        Device deviceForRequest = capabilitiesEngine.getDeviceForRequest(ua1);
        System.out.println(deviceForRequest.getCapability("device_os_version"));
        System.out.println(deviceForRequest.getCapability("brand_name"));
        System.out.println(deviceForRequest.getCapability("model_name"));
        System.out.println(deviceForRequest.getCapability("device_name"));
        System.out.println(deviceForRequest.getCapability("resolution_width"));
        System.out.println(deviceForRequest.getCapability("resolution_height"));
        System.out.println(deviceForRequest.getCapability("is_phone"));
        System.out.println(deviceForRequest.getCapability("density_class"));
        System.out.println(deviceForRequest.getCapability("is_tablet"));
        System.out.println(deviceForRequest.getCapability("is_ios"));
        System.out.println(deviceForRequest.getCapability("is_android"));
        System.out.println(capabilitiesEngine.getDeviceForRequest(ua1).getCapabilities());
        System.out.println(System.currentTimeMillis() - l);
    }


    /**
     * 使用低版本36% ua分析不出来
     * 使用高版本17% ua分析不出来
     * @throws IOException
     */
    @Test
    public void testUa() throws IOException {
        File file = new File("/Users/gavin.xu/Downloads/ua.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line = "";
        double i=1;
        int j=1;
        while ((line = reader.readLine()) != null){
            long l = System.currentTimeMillis();
            Device devide = capabilitiesEngine.getDeviceForRequest(line);
            DeviceDetection deviceDetection = new DeviceDetection();
            deviceDetection.setUa(line);
            deviceDetection.setOsv(devide.getCapability("device_os_version"));
            deviceDetection.setBrand(devide.getCapability("brand_name"));
            deviceDetection.setModel(devide.getCapability("model_name"));
            deviceDetection.setModelFullName(devide.getCapability("device_name"));
            deviceDetection.setWidth(devide.getCapability("resolution_width"));
            deviceDetection.setHeight(devide.getCapability("resolution_height"));
            deviceDetection.setDensity(Float.parseFloat(Optional.ofNullable(devide.getCapability("density_class")).orElse("1.0")));
            String isPhone = devide.getCapability("is_phone");
            String isTablet = devide.getCapability("is_tablet");
            String isIos = devide.getCapability("is_ios");
            String isAndroid = devide.getCapability("is_android");
            OsType osType = OsType.UNKNOW_OS_TYPE;
            DeviceType deviceType = DeviceType.UNKNOW_DEVICE_TYPE;
            if ("true".equals(isPhone)){
                deviceType = DeviceType.PHONE;
            } else if("true".equals(isTablet)){
                deviceType = DeviceType.TABLET;
            }
            if ("true".equals(isIos)){
                osType = OsType.IOS;
            } else if("true".equals(isAndroid)){
                osType = OsType.ANDROID;
            }
            deviceDetection.setOsType(osType);
            deviceDetection.setDeviceType(deviceType);
            //&& !line.contains("MomoChat")
            if (deviceDetection.getBrand().equals("Generic") ){
                System.out.println(i + ":" + (System.currentTimeMillis()-l) + ":" + ObjectMapperUtils.toJSON(deviceDetection));
                i++;
            }
            j++;

        }
        System.out.println(i/j);
    }
}
