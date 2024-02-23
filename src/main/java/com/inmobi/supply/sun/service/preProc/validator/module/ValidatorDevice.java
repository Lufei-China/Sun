package com.inmobi.supply.sun.service.preProc.validator.module;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset;
import com.inmobi.supply.sun.service.preProc.validator.common.UaParser;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.PerfConstant.REQUEST_OS_TYPE;
import static com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset.H;
import static com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset.W;


/**
 * Gavin.xu
 * 2020.5.18
 * 手机型号获取和UA校验
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class ValidatorDevice implements ModuleWorker<Integer> {

    @Resource(name = "sdk")
    private AbstractHandset SDK ;
    @Resource(name = "api")
    private AbstractHandset API ;
    @Autowired
    UaParser uaParser;

    private static final String APPLE_SDK_GENERIC = "apple_sdk_generic";
    private static final String APPLE_SDK_IPOD_GENERIC = "apple_sdk_ipod_generic";
    private static final String APPLE_SDK_IPAD_GENERIC = "apple_sdk_ipad_generic";

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"ValidatorDevice");
        try {
            switch (runTimeContext.getRequestType()) {
                case API_3_1:
                case API_3_2:
                case API_3_3:
                case API_3_3_1:
                case API_3_4:
                case API_3_4_1:
                case API_4_0:
                case API_ORTB_1_0:
                case CUSTOM_API:
                    validate(runTimeContext, runTimeContext.getBaseRequest(), API);
                    break;
                case SDK:
                    validate(runTimeContext, runTimeContext.getSdkRequest(), SDK);
                default:
                    break;
            }
            return Mono.just(1);
        }  catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void validate(RunTimeContext runTimeContext, Object request, AbstractHandset handset) throws Exception {
        LogUtils.logInfo(runTimeContext.getRequestId(),"validateSDK");
        Device.DeviceBuilder builder = Device.builder();
        OsType osType = handset.getOsType(request);
        if (osType == OsType.UNKNOW_OS_TYPE) {
            LogUtils.logInfo(runTimeContext.getRequestId(), "drop by osType {} request:{}", osType, request);
            throw new SunException(TermCode.OS_TYPE_INVALID, runTimeContext);
        }
        DeviceModel deviceModel;
        if (osType == OsType.ANDROID) {
            deviceModel = uaParser.getAndroidModel(runTimeContext);
        } else {
            /**
             * 先拿到开发者传的vendor对ios做特殊处理
             */
            String vendor = handset.getPublisherVendor(request);
            String model = handset.getPublisherModel(request);
            deviceModel = uaParser.getIosModel(runTimeContext, vendor, model);
        }
        String userAgent = runTimeContext.getUserAgent();
        if (null != runTimeContext.getAppModel() && osType.getNumber() != runTimeContext.getAppModel().getPlatformId()) {
            LogUtils.logInfo(runTimeContext.getRequestId(), "os type not equal to app! placementId={}, osType={}, app platform={}",
                    runTimeContext.getPlacementId(), osType, runTimeContext.getAppModel().getPlatformId());
            throw new SunException(TermCode.OS_TYPE_NOT_EQUAL_TO_APP, runTimeContext);
        }
        if (osType == OsType.ANDROID) {
            if (StringUtils.isNotBlank(userAgent) && StringUtils.lowerCase(userAgent).contains("iphone")) {
                //安卓操作系统ua填的ios
                LogUtils.logInfo(runTimeContext.getRequestId(), "os type not equal to ua! placementId={}, osType={}, ua={}",
                        runTimeContext.getPlacementId(), osType, userAgent);
                throw new SunException(TermCode.OS_TYPE_NOT_EQUAL_TO_UA, runTimeContext);
            }
        } else {
            if (StringUtils.isNotBlank(userAgent) && StringUtils.lowerCase(userAgent).contains("android")) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "os type not equal to ua! placementId={}, osType={}, ua={}",
                        runTimeContext.getPlacementId(), osType, userAgent);
                //ios操作系统ua填的android
                throw new SunException(TermCode.OS_TYPE_NOT_EQUAL_TO_UA, runTimeContext);
            }
        }
        PerfUtils.perf(REQUEST_OS_TYPE, Tag.of("os", osType.name()));

        Map<String, Integer> wh = Optional.ofNullable(handset.getHW(request, deviceModel, runTimeContext)).orElse(new HashMap());
        if (MapUtils.isEmpty(wh)) {
            throw new SunException(TermCode.UA_OPTION_NO_WH, runTimeContext);
        }
        Integer orientation = handset.getOrientation(request);
        Float density = handset.getDensity(request, deviceModel);
        ConnectionType connectionType = handset.getConnectionType(request, runTimeContext);

        DeviceType deviceType = handset.getDeviceType(request, deviceModel, osType);
        Carrier carrier = handset.getCarrier(request);
        String model = handset.getModel(osType, request, deviceModel, runTimeContext);
        String vendor = handset.getVendor(request, deviceModel, osType, builder, runTimeContext);
        String osVersion = handset.getOsVersion(request, osType, deviceModel, runTimeContext.getPlacementId());
        String updateMark = handset.getUpdateMark(request, osType, deviceModel);
        String bootMark = handset.getBootMark(request, osType, deviceModel);
        ;
        if (StringUtils.isBlank(osVersion)) {
            PerfUtils.perf("osVersionEmpty", "osType", osType.name());
        }
        if (runTimeContext.isUAPresent()) {
            builder.userAgent(runTimeContext.getUserAgent());
        }
        if (runTimeContext.getRequestType() != RequestType.SDK
            && runTimeContext.getRequestType() != RequestType.API_ORTB_1_0) {
            BaseRequest baseRequest = runTimeContext.getBaseRequest();
            if(baseRequest==null){
                throw new Exception("apiV3Context is null");
            }
//            runTimeContext.getApiContext()
//            if(apiV3Context.getApiV3Request()==null){
//                throw new Exception("ApiV3Request is null");
//            }
            if (baseRequest.getDevice() != null) {
                builder.language(baseRequest.getDevice().getLanguage());
            }
        }
        if (osType == OsType.IOS) {
            String caidCarrier = "";
            //设置ios14参数
            switch (carrier) {
                case MOBILE:
                    caidCarrier = "中国移动";
                    break;
                case UNICOM:
                    caidCarrier = "中国联通";
                    break;
                case TELECOM:
                    caidCarrier = "中国电信";
                    break;
            }
            buildIos14(builder, runTimeContext.getBaseRequest(), osVersion, model, caidCarrier);
        }


        //针对微博ios不标准model 进行处理
        if(runTimeContext.getPlacementModel().getAccountId().equals("9a44504fafba4e7f9822a3a109812edb") && OsType.IOS.getNumber() == osType.getNumber()){
            LogUtils.logInfo(runTimeContext.getRequestId(),"weibo firstModel {}",model);
            model = parseWeiBoModel(model);
            LogUtils.logInfo(runTimeContext.getRequestId(),"weibo lastModel {}",model);
        }

        if (osType == OsType.IOS && StringUtils.isNotBlank(model) && model.contains("iphone")) {
            LogUtils.logInfo(runTimeContext.getRequestId(),"modelIphone {}",model);
            model = model.replace("iphone","iPhone");
        }

        builder.osType(osType)
                .deviceType(deviceType)
                .connectType(connectionType)
                .carrier(carrier)
                .model(model)
                .osVersion(osVersion)
                .vendor(vendor)
                .deviceDensity(density).bootMark(bootMark).updateMark(updateMark)
                .deviceOrientation(orientation)
                .deviceSize(new Device.DeviceSize(wh.getOrDefault(W, 0), wh.getOrDefault(H, 0)));

        runTimeContext.setDevice(builder.build());
    }

    private void buildIos14(Device.DeviceBuilder builder, BaseRequest baseRequest, String osVersion, String model, String carrier) {
        CaidModel caidModel = new CaidModel();
        caidModel.setBootTimeSecond(baseRequest.getDevice().getBootTimeSecond());
        if (!ObjectUtils.isEmpty(baseRequest.getDevice().getGeo())) {
            caidModel.setCountry(baseRequest.getDevice().getGeo().getCountry());
        }
        caidModel.setLanguage(baseRequest.getDevice().getLanguage());
        caidModel.setPhoneNameMd5(baseRequest.getDevice().getPhoneName());
        caidModel.setOsv(osVersion);
        caidModel.setDeviceMachine(model);
        caidModel.setCarrier(carrier);
        caidModel.setMemorySize(baseRequest.getDevice().getMemorySize());
        caidModel.setDiskSize(baseRequest.getDevice().getDiskSize());
        caidModel.setOsUpdateTimeSecond(baseRequest.getDevice().getOsUpdateTimeSecond());
        caidModel.setModelCode(baseRequest.getDevice().getModelCode());
        caidModel.setTimeZone(baseRequest.getDevice().getTimeZone());
        caidModel.setCaid(baseRequest.getDevice().getCaid());
        caidModel.setCaidVersion(baseRequest.getDevice().getCaidVersion());
        caidModel.setPreCaid(baseRequest.getDevice().getPreCaid());
        caidModel.setPreCaidVersion(baseRequest.getDevice().getPreCaidVersion());
        builder.caidModel(caidModel);
    }

    public static String parseWeiBoModel(String model) {
        try{
            PerfUtils.perf("sun.weibo.model.replace", "status","all");
            if(StringUtils.isNotBlank(model) && (model.contains("weibo") || model.startsWith("iphone_"))){
                String newModel = model.replace("iphone_","iphone").replace("__weibo",",weibo");
                String[] split = newModel.split(",");
                if(split.length > 1){
                    PerfUtils.perf("sun.weibo.model.replace", "status","success");
                    return split[0].concat(",").concat(split[1]);
                }else {
                    PerfUtils.perf("sun.weibo.model.replace", "status","error");
                }
            }
        }catch (Exception e){
            log.error("parseWeiBoModel error!",e);
        }
        return model;
    }
}