package com.inmobi.supply.sun.service.preProc.validator.module;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

import cn.inmobi.pb.api.*;
import com.alibaba.fastjson.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.component.model.redis.TemplateModel;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.component.model.redis.placement.RevShare;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.NappDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.AppBundleOsRepo;
import com.inmobi.supply.sun.repo.redis.service.AppRepo;
import com.inmobi.supply.sun.repo.redis.service.PlacementRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset;
import com.inmobi.supply.sun.utils.HttpUtils;
import com.inmobi.supply.sun.utils.IpUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.SUPPORTED_CREATIVE_TYPE;

/**
 * Gavin.xu
 * 2020.5.18
 * Placement，App，Publisher校验
 */
@Service
@Slf4j
public class ValidatorPap implements ModuleWorker<Integer> {
    @Autowired
    private PlacementRepo placementRepo;
    @Autowired
    private AppRepo appRepo;
    @Autowired
    private AppBundleOsRepo appBundleOsRepo;
    @Resource(name = "sdk")
    private AbstractHandset SDK ;
    @Resource(name = "api")
    private AbstractHandset API ;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"ValidatorPap");
        try {
            long placementId = runTimeContext.getPlacementId();

            PlacementModel placementModel = placementRepo.getPlacement(placementId);
            if (placementModel == null) {
                throw new SunException(TermCode.PLACE_NOT_FOUND, runTimeContext);
            }
            PerfUtils.perf("sun.request", "pubAccountId", placementModel.getAccountId().concat("_").concat(placementModel.getAccountName()));

            runTimeContext.setPlacementModel(placementModel);
            AppModel appModel = null;
            OsType osType = OsType.forNumber(placementModel.getOsType());
            runTimeContext.setOsType(osType);

            if(null == osType || (osType.getNumber()!=1 &&  osType.getNumber()!=2)){
                LogUtils.logInfo(runTimeContext.getRequestId(),"osTypeIsNull {}",osType);
                PerfUtils.perf("sun.osType.isError","placement",String.valueOf(placementId));
            }
            String bundle = runTimeContext.getRequestBundleId();
            if(StringUtils.isBlank(bundle)){
                PerfUtils.perf("sun.adx.bundle.drop","status","bundleIsNull");
                throw new SunException(TermCode.APP_BUNDLE_REQUIRED, runTimeContext);
            }
            Integer trafficSources = placementModel.getTrafficSources();
            appModel = appBundleOsRepo.getAppModel(bundle, osType.getNumber());

            if(appModel == null && TrafficSourcesEnum.ADX.getCode() != placementModel.getTrafficSources()){
                if(!DynamicListConfig.NOT_ADX_BUNDLE_WHITE.get().contains(placementModel.getAccountId())){
                    PerfUtils.perf("sun.adx.bundle.null","placement",String.valueOf(placementId));
                    throw new SunException(TermCode.APP_NOT_FOUND, runTimeContext);
                }
            }

            if (DynamicListConfig.NOT_ADX_BUNDLE_WHITE.get().contains(placementModel.getAccountId())) {
                //白名单不进行bundle 校验
                PerfUtils.perf("sun.adx.bundle.white","placement",String.valueOf(placementId));
            } else if (TrafficSourcesEnum.ADX.getCode() != placementModel.getTrafficSources() && appModel.getId() != placementModel.getInmobiAppDictId().intValue()) {
                //渠道校验
                //请求bundle对应的app信息 与 平台广告位对应app信息 不一致
                PerfUtils.perf("sun.adx.bundleNotEqual.drop", "pub", placementModel.getAccountId());
                throw new SunException(TermCode.APP_BUNDLE_REQUIRED, runTimeContext);
            }
            runTimeContext.setAppModel(appModel);
            //1.check placement
            checkPlacementModel(runTimeContext, placementModel);
            //2.check appModel adx不进行校验
            if(trafficSources != TrafficSourcesEnum.ADX.getCode() && !DynamicListConfig.NOT_ADX_BUNDLE_WHITE.get().contains(placementModel.getAccountId())){
                checkAppModel(runTimeContext, appModel);
            }

            if(null == appModel){
                PerfUtils.perf("sun.appModel.isNull","pub",placementModel.getAccountId());
            }

            //3.check ip
            checkIp(runTimeContext);
            //4.check bundle
            checkBundleId(runTimeContext);
            return Mono.just(0);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void checkIp(RunTimeContext runTimeContext) throws SunException {
        RequestType requestType = runTimeContext.getRequestType();
        InetSocketAddress inetSocketAddress = runTimeContext.getServerRequest().localAddress().orElse(null);
        InetSocketAddress remoteAddress = runTimeContext.getServerRequest().remoteAddress().orElse(null);
        String hostAddress = "";
        if(remoteAddress != null){
             hostAddress = remoteAddress.getAddress().getHostAddress();
        }
        if (inetSocketAddress == null) {
            throw new SunException(TermCode.SDK_IP_EMPTY, runTimeContext);
        }
        String ipAddress = "";
        try{
            ipAddress = HttpUtils.getIpAddress(runTimeContext.getServerRequest());
        }catch (Exception e){
            PerfUtils.perf("sun.ipAddress.error", Tag.of("type", "ipError"));
            LogUtils.logError(runTimeContext.getRequestId(),"ipAddressError {}",e);
        }
        String ipV4;
        String ipV6 = "";
        runTimeContext.setServerIp(inetSocketAddress.getHostName());
        if (requestType.isSdk()) {
            ipV4 = getSdkIp(ipAddress,hostAddress,runTimeContext);
        } else {
            LogUtils.logInfo(runTimeContext.getRequestId(),"ipAddressApi {},headers {}", ipAddress,JSON.toJSONString(runTimeContext.getHeaders()));
            ipV4 = runTimeContext.getBaseRequest().getDevice().getIp();
            ipV6 = runTimeContext.getBaseRequest().getDevice().getIpv6();

            /**
             * 移动端补量 ip 获取特殊处理
             */
            if (DynamicListConfig.BRUSH_FLOW_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())
                    && DynamicListConfig.BRUSH_FLOW_BUNDLE_LIST.get().contains(runTimeContext.getRequestBundleId())) {
                ipV4 = getSdkIp(ipAddress,hostAddress,runTimeContext);
                PerfUtils.perf("sun.brush.ip");
            }

            if (StringUtils.isBlank(ipV4) && StringUtils.isBlank(ipV6)) {
                throw new SunException(TermCode.API_IP_EMPTY, runTimeContext);
            }
            /**
             * 防止开发者误传ipv6到ipv4
             */
            if (IpUtils.isValidIpV6(ipV4)) {
                ipV6 = ipV4;
            }
            if (IpUtils.isValidIpV4(ipV6)) {
                ipV4 = ipV6;
            }
        }
        PerfUtils.perf("sun.ip.type", Tag.of("type", "total"));
        boolean isValidIpV4 = StringUtils.isNotBlank(ipV4) && IpUtils.isValidIpV4(ipV4);
        boolean isValidIpV6 = StringUtils.isNotBlank(ipV6) && IpUtils.isValidIpV6(ipV6);
        if (isValidIpV4) {
            PerfUtils.perf("sun.ip.type", Tag.of("type", "ipv4"));
            runTimeContext.setUserIpV4(ipV4);
        }
        if (isValidIpV6) {
            PerfUtils.perf("sun.ip.type", Tag.of("type", "ipv6"));
            runTimeContext.setUserIpV6(ipV6);
        }
        if (!isValidIpV4 && !isValidIpV6) {
            throw new SunException(TermCode.IP_INVALID, runTimeContext);
        }
        if(StringUtils.isNotBlank(runTimeContext.getUserIpV4()) && runTimeContext.getUserIpV4().equals(runTimeContext.getUserIpV6())){
            LogUtils.logInfo(runTimeContext.getRequestId(),"ipv4EqualIpv6:{}",runTimeContext.getRequestId());
        }
    }

    private String getSdkIp(String ipAddress, String hostAddress,RunTimeContext runTimeContext) throws SunException {
        Map<String, String> headers = runTimeContext.getHeaders();
        /**
         * 兼容uat-api-w.inmobi.cn的sdk的真实ip投放
         */
        String finalIp = null;
        String firstIp = headers.get(StringUtils.lowerCase(RequestHeader.X_Inmobi_IP));
        String realIp = headers.get(StringUtils.lowerCase(RequestHeader.X_REAL_IP));
        String forwardedIp = headers.get(StringUtils.lowerCase(RequestHeader.X_FORWARD_FOR));
        if (StringUtils.isNotBlank(firstIp)) {
            finalIp = firstIp;
        } else if (StringUtils.isNotBlank(realIp)) {
            finalIp = realIp;
        } else if (StringUtils.isNotBlank(forwardedIp)) {
            finalIp = forwardedIp;
        } else if (StringUtils.isNotBlank(ipAddress)){
            finalIp = ipAddress;
        }
        LogUtils.logInfo(runTimeContext.getRequestId(), "sdkIp:{},headers:{}", finalIp, JSON.toJSONString(headers));
        LogUtils.logInfo(runTimeContext.getRequestId(), "ipAddressSdk {},finalIp {},headers {}", ipAddress,finalIp,JSON.toJSONString(headers));
        if (StringUtils.isBlank(finalIp)) {
            throw new SunException(TermCode.SDK_IP_EMPTY, runTimeContext);
        }
        if(!hostAddress.equals(finalIp)){
            PerfUtils.perf("sun.skd.finalIp.notEquals.hostAddress", Tag.of("type", "notEquals"));
        }
         return StringUtils.split(finalIp, ",")[0];
    }

    private void checkBundleId(RunTimeContext runTimeContext) throws SunException {
        if (StringUtils.isBlank(runTimeContext.getRequestBundleId())) {
            PerfUtils.perf("sun.adx.bundle.drop","status","requestBundleIdIsNull");
            throw new SunException(TermCode.APP_BUNDLE_REQUIRED, runTimeContext);
        }
    }

    private void checkPlacementModel(RunTimeContext runTimeContext, PlacementModel placementModel) throws SunException {
        if (placementModel == null) {
            throw new SunException(TermCode.PLACE_NOT_FOUND, runTimeContext);
        }
        boolean validPlacement = placementModel.getId() != null
                && StringUtils.isNotBlank(placementModel.getName())
                && placementModel.getType() != null
                && placementModel.getPubExpectation() != null
                && placementModel.getRevShareType() != null
                && CollectionUtils.isNotEmpty(placementModel.getTemplateList());
        if (!validPlacement) {
            throw new SunException(TermCode.PLACE_INVALID, runTimeContext);
        }

        //ADX 流量不校验 appId
        if(placementModel.getTrafficSources() == TrafficSourcesEnum.DIRECT.getCode() || placementModel.getTrafficSources() == TrafficSourcesEnum.AGENCY.getCode()){
            if(placementModel.getInmobiAppDictId() == null){
                PerfUtils.perf("sun.adx.inmobiAppDictIdIsNull.drop","pub",placementModel.getAccountId());
                throw new SunException(TermCode.PLACE_INVALID, runTimeContext);
            }
        }

        //check adType
        AdType adType = AdType.forNumber(placementModel.getType());
        if (adType == null
                || adType == AdType.UNKNOW_AD_TYPE) {
            throw new SunException(TermCode.AD_TYPE_INVALID, runTimeContext);
        }

        //check unsupported adType(M0 only support SPLASH)
        if (!DynamicListConfig.SUPPORTED_AD_TYPE.get().contains(adType.getNumber())) {
            throw new SunException(TermCode.AD_TYPE_UNSUPPORTED, runTimeContext);
        }
        runTimeContext.setAdType(adType);

        //check revShareType
        RevenueShareType revenueShareType = RevenueShareType.forNumber(placementModel.getRevShareType());
        if (revenueShareType == null
                || revenueShareType == RevenueShareType.UNRECOGNIZED) {
            throw new SunException(TermCode.REVENUE_SHARE_TYPE_INVALID, runTimeContext);
        }
        runTimeContext.setRevenueShareType(revenueShareType);

        if (CollectionUtils.isEmpty(placementModel.getFixRevShareList())) {
            List<RevShare> defaultRevShareList = new ArrayList<>();
            Arrays.stream(FloorEntityType.values())
                    .filter(floorEntityType -> floorEntityType != FloorEntityType.UNKNOW_FLOOR_ENTITY_TYPE && floorEntityType != FloorEntityType.UNRECOGNIZED)
                    .forEach(floorEntityType -> defaultRevShareList.add(new RevShare(floorEntityType.getNumber(), AuctionParam.DEFAULT_REV_SHARE)));
            placementModel.setFixRevShareList(defaultRevShareList);
        }
        List<CreativeType> validCreativeTypeList = new ArrayList<>();
        List<TemplateModel> validTemplateModel = placementModel.getTemplateList().stream()
                .filter(templateModel -> {
                    if (templateModel == null
                            || templateModel.getDcCreativeTypeId() == null) {
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel creativeType is null! model:{}", templateModel);
                        return false;
                    }
                    CreativeType creativeType = CreativeType.forNumber(templateModel.getDcCreativeTypeId());
                    if (creativeType == null || creativeType == CreativeType.UNKNOW_CREATIVE_TYPE) {
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel unknown creativeType! model:{}", templateModel);
                        return false;
                    }
                    if (!SUPPORTED_CREATIVE_TYPE.get().contains(templateModel.getDcCreativeTypeId())) {
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel global unsupported creativeType! model:{}", templateModel);
                        return false;
                    }
                    if (creativeType == CreativeType.IMAGE && StringUtils.isBlank(templateModel.getDcAspectRatio())) {
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel image need ratio! model:{}", templateModel);
                        return false;
                    }
                    if (creativeType == CreativeType.VIDEO
                            && (templateModel.getVideoDuration() == null || templateModel.getVideoDuration() == 0)) {
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel video need second! model:{}", templateModel);
                        //video必须指定视频时长
                        return false;
                    }
                    if (adType == AdType.FEED && (templateModel.getCustomFeedStyle() == null
                            || TemplateLayOut.forNumber(templateModel.getCustomFeedStyle()) == null
                            || TemplateLayOut.forNumber(templateModel.getCustomFeedStyle()) == TemplateLayOut.UNKNOW_TEMPLATE_DISPLAY)) {
                        //feed流视频必须有样式
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel feed need feedStyle! model:{}", templateModel);
                        return false;
                    }
                    if (runTimeContext.getRequestType().isSdk() && templateModel.getWidgetSdk() == null) {
                        //sdk 请求必须指定相应的模板
                        LogUtils.logWarn(runTimeContext.getRequestId(), "filter templateModel sdk need template vm! model:{}", templateModel);
                        return false;
                    }
                    validCreativeTypeList.add(creativeType);
                    return true;
                }).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(validTemplateModel)) {
            throw new SunException(TermCode.TEMPLATE_INVALID, runTimeContext);
        }

        placementModel.setTemplateList(validTemplateModel);
        if (CollectionUtils.isEmpty(validCreativeTypeList)) {
            throw new SunException(TermCode.TEMPLATE_CREATIVE_INVALID, runTimeContext);
        }
        if (placementModel.getPubExpectation() <= 0L) {
            throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
        }

        // 开发者低价
        if(null != placementModel.getFloorPriceFlag() &&  placementModel.getFloorPriceFlag() == 1){
            if(runTimeContext.getBaseRequest().getImp().getPriceFloor() <= 0){
                throw new SunException(TermCode.MISS_FLOOR_PRICE, runTimeContext);
            }
        }

        //reset to valid placementList
        //runTimeContext.setPlacementCreativeTypes(validCreativeTypeList);
    }

    private void checkAppModel(RunTimeContext runTimeContext, AppModel appModel) throws SunException {
        if (appModel == null) {
            PerfUtils.perf("sun.adx.bundle.drop","status","appModelIsNull");
            throw new SunException(TermCode.APP_NOT_FOUND, runTimeContext);
        }
        String bundle = runTimeContext.getRequestBundleId();
        boolean validAppModel = StringUtils.isNotBlank(appModel.getBundleId()) && appModel.getPlatformId() != null;
        //log.info("baidu:" + ObjectMapperUtils.toJSON(DynamicListConfig.ADAPTER_BAIDU_LIST.get()));
        //log.info("baidu spec:" + DynamicListConfig.ADAPTER_BAIDU_LIST.get().contains(runTimeContext.getPlacementId()));


        //if (appModel.getPubAccountId() != null &&(appModel.getPubAccountId().equals("3775726195e0540ecbe4a5bb821c9845")||appModel.getPubAccountId().equals("b2fe7de286ba6330e460d9fbeb62c03a"))) {
        if (appModel.getPubAccountId() != null && (DynamicListConfig.APP_BUNDLE_WHITE.get().contains(appModel.getPubAccountId()))) {
//            PerfUtils.perf("baidu.bundle", Tag.of("bundle", Optional.ofNullable(bundle).orElse("unknown")));
        } else {
            validAppModel = validAppModel && (StringUtils.lowerCase(appModel.getBundleId()).equals(StringUtils.lowerCase(bundle))
                    || StringUtils.lowerCase(appModel.getBundleId()).contains(StringUtils.lowerCase(bundle))
                    || StringUtils.lowerCase(bundle).contains(StringUtils.lowerCase(appModel.getBundleId())));
        }
        if (!validAppModel) {
            runTimeContext.getTermCodes().add(TermCode.APP_INVALID);
        }
        if (StringUtils.isBlank(bundle) && StringUtils.isBlank(appModel.getBundleId())) {
            throw new SunException(TermCode.APP_INVALID, runTimeContext);
        }

        //ADX 流量如果有传入 bundleid app_name 进行替换   ADX 改造前数据继续进行bundle替换 全量上线 delete
//        try {
//            RequestAdapterResponse requestAdapterResponse = runTimeContext.getRequestAdapterResponse();
//            if (null != requestAdapterResponse) {
//                PerfUtils.perf("sun.adx.replace.info","pub",runTimeContext.getPlacementModel().getAccountId().concat(runTimeContext.getPlacementModel().getAccountName()));
//                JsonObject publisherSpecificFields = requestAdapterResponse.getPublisherSpecificFields();
//                int adxFlag = 1;
//                if (!Objects.isNull(publisherSpecificFields)) {
//                    JsonElement accountType = publisherSpecificFields.get("accountType");
//                    if (accountType != null) {
//                        adxFlag = accountType.getAsInt();
//                    }
//                }
//                runTimeContext.setAccountType(adxFlag);
//                if (!Objects.isNull(publisherSpecificFields)) {
//                    if (2 == adxFlag && StringUtils.isNotBlank(bundle)) {
//                        //ADX 流量 替换bundle 和 appName  以adx为主
//                        AppModel appModelNew = new AppModel();
//                        BeanUtils.copyProperties(appModel,appModelNew);
//                        appModelNew.setBundleId(bundle);
//                        if (StringUtils.isNotBlank(runTimeContext.getBaseRequest().getApp().getName())) {
//                            appModelNew.setAppName(runTimeContext.getBaseRequest().getApp().getName());
//                        }
//                        runTimeContext.setAppModel(appModelNew);
//                        PerfUtils.perf("sun.adx.replace.info","pub",runTimeContext.getPlacementModel().getAccountId().concat(runTimeContext.getPlacementModel().getAccountName()));
//                    }
//                }
//            }
//        } catch (Exception e) {
//            LogUtils.logError(runTimeContext.getRequestId(), "ValidatorPap adx checkAppModel error {}", e);
//        }

        //TODO placement & app 联合校验
        OsType osType = OsType.forNumber(appModel.getPlatformId());
        if (osType == null || osType == OsType.UNKNOW_OS_TYPE) {
            throw new SunException(TermCode.OS_TYPE_INVALID, runTimeContext);
        }
    }
}