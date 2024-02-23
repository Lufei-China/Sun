package com.inmobi.supply.sun.service.preProc.prepare.module;

import cn.inmobi.pb.api.*;
import cn.inmobi.pb.saturn.custom.BidMethod;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.component.model.redis.TemplateModel;
import com.inmobi.supply.component.model.redis.placement.PlacementCustomize;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicMapConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.SdkParam;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.inmobi.supply.sun.utils.WorkerUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

import static cn.inmobi.pb.api.InteractionType.*;
import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.MKT_FLAG;
import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.SANDBOX_REQUEST_ENABLE;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.BES_PLACEMENT_LIST;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.SKAN_APP_LIST;
import static com.inmobi.supply.sun.constant.PerfConstant.REQUEST_AD_TYPE;
import static com.inmobi.supply.sun.constant.RuntimeConstant.*;

/**
 * build bidRequest
 *
 * @author remi.li
 */

@Service
@Slf4j
public class PrepareBidRequest implements ModuleWorker<BidRequest> {
    @Override
    public Mono<BidRequest> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"PrepareBidRequest");
        try {
            PerfUtils.perf(REQUEST_AD_TYPE,
                    Tag.of("adType", runTimeContext.getAdType().name()),
                    Tag.of("os", runTimeContext.getDevice().getOsType().name()),
                    Tag.of("requestType", runTimeContext.getRequestType().name()));
            BidRequest.Builder bidRequestBuilder = buildBidRequest(runTimeContext);
            runTimeContext.setBidRequest(bidRequestBuilder.build());
            return Mono.just(bidRequestBuilder.build());
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private BidRequest.Builder buildBidRequest(RunTimeContext runTimeContext) throws SunException {
        BidRequest.Builder bidRequestBuilder = BidRequest.newBuilder();
        bidRequestBuilder.setRequestId(runTimeContext.getRequestId());

        if (!runTimeContext.getRequestType().isSdk()) {
            bidRequestBuilder.setApiVersion(runTimeContext.getRequestType().getVersion());
        } else {
            bidRequestBuilder.setSdkVersion(StringUtils.isNotBlank(runTimeContext.getSdkVersion()) ? runTimeContext.getSdkVersion() : "");
        }
        RequestType requestType = runTimeContext.getRequestType();
        if (requestType.isSdk()) {
            bidRequestBuilder.setIntegrationType(IntegrationType.SDK_VALUE);
        } else {
            bidRequestBuilder.setIntegrationType(IntegrationType.API_VALUE);
        }
        if (CollectionUtils.isNotEmpty(runTimeContext.getNappExperiments())) {
            bidRequestBuilder.addAllNappExperiments(runTimeContext.getNappExperiments());
        }
        bidRequestBuilder.setPubAccountId(runTimeContext.getPlacementModel().getAccountId());
        bidRequestBuilder.setUa(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""));
        //TODO make sure which ip to be choose
        bidRequestBuilder.setIp(Optional.ofNullable(runTimeContext.getUserIpV4()).orElse(""));
        bidRequestBuilder.setIpv6(Optional.ofNullable(runTimeContext.getUserIpV6()).orElse(""));
        if (SANDBOX_REQUEST_ENABLE.get()) {
            if (requestType == RequestType.SDK) {
                bidRequestBuilder.setIp("61.49.105.28");
            } else {
                bidRequestBuilder.setIp(runTimeContext.getBaseRequest().getDevice().getIp());
            }
        }
        if (runTimeContext.getPlacementModel().getAccountId().equals("550d78c18791d7e161e788ed734eb064")) {
            bidRequestBuilder.setIp("61.49.105.28");
        }
        bidRequestBuilder.setGeo(Optional.ofNullable(runTimeContext.getGeo()).orElse(Geo.getDefaultInstance()));
        bidRequestBuilder.setNappScore(runTimeContext.getEarthResponse().getScore());
        bidRequestBuilder.setTencentScore(runTimeContext.getEarthResponse().getTencentScore());
        bidRequestBuilder.setAppFrequency(runTimeContext.getEarthResponse().getAppFrequency());
        buildUser(runTimeContext, bidRequestBuilder);
        buildApp(runTimeContext, bidRequestBuilder);
        buildDevice(runTimeContext, bidRequestBuilder);
        if (SANDBOX_REQUEST_ENABLE.get()) {
            bidRequestBuilder.setTest(runTimeContext.getAdType().equals(AdType.SPLASH) ? SANDBOX_REQUEST_ID : SANDBOX_OPEN_RTB_REQUEST_ID);
            //bidRequestBuilder.setTest(SANDBOX_VEDIO_REQUEST_ID);
        }
        bidRequestBuilder.setFloor(runTimeContext.getBaiduBrice());
        buildAdSlot(runTimeContext, bidRequestBuilder);
        setTestFlag(runTimeContext, bidRequestBuilder);
        /**
         * 传入媒体CI平台的timeout 或者开发者传入的超时时间
         */
        if(null != runTimeContext.getTmax() && runTimeContext.getTmax() > 0 && DynamicListConfig.ORTB_PUBLISHER_LIST.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
            bidRequestBuilder.setTimeout(runTimeContext.getTmax());
        }else if (null != runTimeContext.getBaseRequest() && runTimeContext.getBaseRequest().getTmax() != 0) {
            bidRequestBuilder.setTimeout(runTimeContext.getBaseRequest().getTmax());
        }else {
            bidRequestBuilder.setTimeout(Optional.ofNullable(runTimeContext.getPlacementModel().getTimeOut()).orElse(200));
        }
        /*if (DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getAppModel().getPubAccountId())) {
            if (runTimeContext.getAdType() == AdType.FEED) {
                int timeOut =DynamicIntegerConfig.SATURN_BAIDU_FEED_AD_POOL.get().intValue();
                bidRequestBuilder.setTimeout(Optional.ofNullable(timeOut).orElse(200));
            } else {
                int timeOut = DynamicIntegerConfig.SATURN_BAIDU_AD_POOL.get().intValue();
                bidRequestBuilder.setTimeout(Optional.ofNullable(timeOut).orElse(200));            }
        }*/
        bidRequestBuilder.setLat(runTimeContext.getLat());
        bidRequestBuilder.setAdxFlag(runTimeContext.getAccountType());

        //配置 marketPlace 的广告位
//        Long placementId = runTimeContext.getPlacementModel().getId();
//        Map<?, ?> map = MARKET_PLACE_PLACEMENT.get();
//        LogUtils.logInfo(runTimeContext.getRequestId(),"marketPlacementName {}", JSON.toJSONString(map));
//        String placementName = (String) map.get(String.valueOf(placementId));
//        if (StringUtils.isNotBlank(placementName)) {
//            PerfUtils.perf("sun.market.place.bidRequest", "placement", String.valueOf(runTimeContext.getPlacementId()));
//            runTimeContext.setSwitchFlag(1);
//            bidRequestBuilder.setSwitchFlag(1);
//        }

        //开关控制 是否走 mkt 竞价逻辑
        if (MKT_FLAG.get()) {
            PerfUtils.perf("sun.mkt.place.count", "status", "request");
            runTimeContext.setSwitchFlag(1);
            bidRequestBuilder.setSwitchFlag(1);
        }

        if(BES_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())){
            PerfUtils.perf("baidu.box.info","type","bidRequest");
            bidRequestBuilder.setExt("baidu");
        }
        return bidRequestBuilder;
    }

    private void setTestFlag(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) {
        boolean isTestTraffic = Optional.ofNullable(runTimeContext.getTestTraffic()).orElse(false);
        if (isTestTraffic) {
            bidRequestBuilder.setTest("1");
        } else {
            bidRequestBuilder.setTest("0");
        }
    }

    private void buildUser(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) {
        User.Builder userBuilder = User.newBuilder();
        if (StringUtils.isNotBlank(runTimeContext.getCsids())) {
            userBuilder.setCsid(runTimeContext.getCsids());
        }
        if (CollectionUtils.isNotEmpty(runTimeContext.getBaiduTagIdList())) {
            runTimeContext.getBaiduTagIdList().forEach(b -> {
                if (StringUtils.isNotBlank(b) && DynamicMapConfig.CSID_MAPPING.get().get(b) != null) {
                    b = DynamicMapConfig.CSID_MAPPING.get().get(b).toString();
                }
                if (StringUtils.isNotBlank(userBuilder.getCsid())) {
                    userBuilder.setCsid(userBuilder.getCsid() + "," + b);
                } else {
                    userBuilder.setCsid(b);
                }
            });
        }
        // bes  user_category 透传
        if (CollectionUtils.isNotEmpty(runTimeContext.getBesUserCategory())) {
            userBuilder.addAllUserCategory(runTimeContext.getBesUserCategory());
        }

        try {
            //支持 API 新增tagId
            LogUtils.logInfo(runTimeContext.getRequestId(), "tagIdBefore {}", userBuilder.getCsid());
            if (null != runTimeContext.getBaseRequest()) {
                cn.inmobi.pb.sun.rr.base.User user = runTimeContext.getBaseRequest().getUser();
                if (CollectionUtils.isNotEmpty(user.getTagIdList())) {
                    user.getTagIdList().forEach(tagId -> {
                        if (StringUtils.isNotBlank(tagId)) {
                            if (StringUtils.isNotBlank(userBuilder.getCsid())) {
                                userBuilder.setCsid(userBuilder.getCsid() + "," + tagId);
                            } else {
                                userBuilder.setCsid(tagId);
                            }
                        }
                    });
                }
                LogUtils.logInfo(runTimeContext.getRequestId(), "tagIdAfter {}", userBuilder.getCsid());
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "addTagIdError", e);
        }

        BaseRequest baseRequest = runTimeContext.getBaseRequest();
        if(!Objects.isNull(baseRequest)){
            String appList = baseRequest.getUser().getAppList();
            if(StringUtils.isNotBlank(appList)){
                userBuilder.setAppList(appList);
            }
        }
        bidRequestBuilder.setUser(userBuilder.build());
        runTimeContext.setAllCsids(userBuilder.getCsid());
    }

    private void buildAdSlot(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) throws SunException {
        AdSlot.Builder adSlotBuilder = AdSlot.newBuilder();
        //only one adSlot
        if (runTimeContext.isSecure()) {
            adSlotBuilder.setIsSecure(1);
        } else {
            adSlotBuilder.setIsSecure(0);
        }
        adSlotBuilder.addAcceptedBidMethod(BidMethod.CPM_VALUE);
        if (DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getPlacementModel().getAccountId())) {
            adSlotBuilder.addAcceptedBidMethod(BidMethod.CPC_VALUE);
            //支持 oCpm 竞价
            if(StringUtils.isNotBlank(runTimeContext.getAttributeInfo()) && (runTimeContext.getAttributeInfo().equals("4") || runTimeContext.getAttributeInfo().equals("15"))){
                adSlotBuilder.addAcceptedBidMethod(BidMethod.OCPC_VALUE);
            }
        }
        adSlotBuilder.setId(String.valueOf(runTimeContext.getPlacementId())); // AdSlot.id is i64
        adSlotBuilder.setAdType(runTimeContext.getAdType().getNumber());
        if (runTimeContext.getPlacementModel().getAuditStyle() == null) {
            runTimeContext.getPlacementModel().setAuditStyle(1);
        }
        adSlotBuilder.setAuditStyle(runTimeContext.getPlacementModel().getAuditStyle());
        PlacementCustomize placementCustomize = runTimeContext.getPlacementModel().getPlacementCustomize();
        adSlotBuilder.putAllFloorCpm(runTimeContext.getFloorEntityMap().entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().getNumber(), Map.Entry::getValue, (o1, o2) -> o1)));
        List<CreativeTypeRatio> acceptedCreativeTypeRatioList = new ArrayList<>();

        List<TemplateModel> validTemplateModelList = runTimeContext.getPlacementModel().getTemplateList();

        for (TemplateModel templateModel : validTemplateModelList) {
            if (!runTimeContext.getRequestType().isSdk()
                    && CollectionUtils.isNotEmpty(runTimeContext.getBaseRequest().getExt().getCreativeSupportList())) {
                //请求中指定要的创意类型
                if (!runTimeContext.getBaseRequest().getExt().getCreativeSupportList().contains(templateModel.getDcCreativeTypeId())) {
                    continue;
                }
            }
            if (templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                if (!runTimeContext.getRequestType().isSupportJsonVideo() ||
                        (runTimeContext.getRequestType().isSdk() && StringUtils.isEmpty(runTimeContext.getSdkVersion()))
                        || (runTimeContext.getRequestType().isSdk() && !StringUtils.isEmpty(templateModel.getScMinimalSdkVersion())
                        && runTimeContext.getSdkVersion().compareToIgnoreCase(templateModel.getScMinimalSdkVersion()) < 0)) {
                    continue;
                }
            }
            //check placement customize param
            if (placementCustomize != null) {
                ConnectionType connectionType = runTimeContext.getDevice().getConnectType();
                if (placementCustomize.getIsStaticWifi() == 0) {
                    //not support static ad when network is wifi
                    if (connectionType == ConnectionType.WIFI
                            && (templateModel.getDcCreativeTypeId() == CreativeType.IMAGE_VALUE
                            || templateModel.getDcCreativeTypeId() == CreativeType.GIF_VALUE)) {
                        continue;
                    }
                }

                if (placementCustomize.getIsStaticNonWifi() == 0) {
                    //not support static ad when network is not wifi
                    if (connectionType != ConnectionType.WIFI
                            && (templateModel.getDcCreativeTypeId() == CreativeType.IMAGE_VALUE
                            || templateModel.getDcCreativeTypeId() == CreativeType.GIF_VALUE)) {
                        continue;
                    }
                }

                if (placementCustomize.getIsVideoWifi() == 0) {
                    //not support video ad when network is wifi
                    if (connectionType == ConnectionType.WIFI
                            && templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                        continue;
                    }
                }

                if (placementCustomize.getIsVideoNonWifi() == 0) {
                    //not support video ad when network is not wifi
                    if (connectionType != ConnectionType.WIFI
                            && templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                        continue;
                    }
                }
//                adSlotBuilder.setDeeplinkSupport(Optional.ofNullable(placementCustomize.getSupportDeeplink()).orElse(1));
                // 修改成动态
            }
            CreativeTypeRatio.Builder builder = CreativeTypeRatio.newBuilder()
                    .setCreativeType(templateModel.getDcCreativeTypeId())
                    .setWhRatio(templateModel.getDcAspectRatio());
            if (templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                builder.setMaxVideoDuration(templateModel.getVideoDuration());
            }
            if (runTimeContext.getAdType() == AdType.FEED) {
                builder.setFeedLayout(templateModel.getCustomFeedStyle());
            } else {
                builder.setFeedLayout(TemplateLayOut.UNKNOW_TEMPLATE_DISPLAY_VALUE);
            }
            acceptedCreativeTypeRatioList.add(builder.build());
            runTimeContext.getPlacementCreativeTypes().add(CreativeType.forNumber(templateModel.getDcCreativeTypeId()));
            adSlotBuilder.addCreativeSupport(templateModel.getDcCreativeTypeId());
        }

        /*if (!runTimeContext.getRequestType().isSdk()) {
            if (CollectionUtils.isNotEmpty(runTimeContext.getApiv3Request().getExt().getCreativeSupportList())) {
                //如果请求携带，使用请求中的数据进行覆盖
                adSlotBuilder.clearCreativeSupport().addAllCreativeSupport(runTimeContext.getApiv3Request().getExt().getCreativeSupportList());
            }
        }*/

        if (CollectionUtils.isEmpty(acceptedCreativeTypeRatioList)) {
            throw new SunException(TermCode.NO_SUITABLE_CREATIVE_TYPE, runTimeContext);
        }
        adSlotBuilder.addAllAcceptedCreativeTypeRatios(acceptedCreativeTypeRatioList);

        List<Integer> listInteractionType = runTimeContext.getPlacementModel().getListInteractionType();
        List<Integer> acceptedInteractionTypeList = new ArrayList<>();
        //默认支持所有交互类型
        if(CollectionUtils.isEmpty(listInteractionType)){
            acceptedInteractionTypeList.addAll(Arrays.asList(DOWNLOAD_VALUE, OPEN_IN_APP_VALUE, DEEPLINK_VALUE, WECHAT_MINI_PROGRAM_VALUE));
            adSlotBuilder.setDeeplinkSupport(1);
        }else{
            acceptedInteractionTypeList.addAll(listInteractionType);
            if(listInteractionType.contains(DEEPLINK_VALUE)){
                adSlotBuilder.setDeeplinkSupport(1);
            }
        }

//        Arrays.stream(InteractionType.values())
//                .filter(interactionType -> interactionType != UNRECOGNIZED
//                        && interactionType != NO_INTERACTION
//                        && interactionType != UNKNOW_INTERACTION_TYPE)
//                .forEach(interactionType -> {
//                    Integer isSuportLink = 1;
//                    if (interactionType == InteractionType.DEEPLINK) {
//                        if (runTimeContext.getRequestType().isSdk() && (isSuportLink.equals(1))) {
//                            //sdk always support deeplink
//                            acceptedInteractionTypeList.add(interactionType.getNumber());
//                        } else {
//                            boolean isSupportDeeplink = true;
//                            if (runTimeContext.getBaseRequest() != null && runTimeContext.getBaseRequest().getExt() != null) {
//                                if (runTimeContext.getRequestType() == RequestType.API_3_1) {
//                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getExternalSupported();
//                                } else if (runTimeContext.getRequestType() == RequestType.API_3_4_1 || runTimeContext.getRequestType() == RequestType.API_4_0) {
//                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getInteractSupportList().contains(3);
//                                } else {
//                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getSupportDeeplink();
//                                }
//                            }
//
//                            if (isSupportDeeplink && (isSuportLink.equals(1))) {
//                                //api support deeplink only when SupportDeeplink param is true
//                                acceptedInteractionTypeList.add(interactionType.getNumber());
//                            }
//                        }
//                    } else {
//                        if (runTimeContext.getRequestType() == RequestType.API_3_4_1 || runTimeContext.getRequestType() == RequestType.API_4_0) {
//                            if (interactionType == InteractionType.OPEN_IN_APP
//                                    && runTimeContext.getBaseRequest().getExt() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList().contains(1)) {
//                                acceptedInteractionTypeList.add(interactionType.getNumber());
//                            }
//                            if (interactionType == InteractionType.DOWNLOAD
//                                    && runTimeContext.getBaseRequest().getExt() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList().contains(2)) {
//                                acceptedInteractionTypeList.add(interactionType.getNumber());
//                            }
//                            if (interactionType == InteractionType.WECHAT_MINI_PROGRAM
//                                    && runTimeContext.getBaseRequest().getExt() != null && runTimeContext.getBaseRequest().getExt() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList() != null && runTimeContext.getBaseRequest().getExt().getInteractSupportList().contains(4)) {
//                                acceptedInteractionTypeList.add(interactionType.getNumber());
//                            }
//                        } else {
//                            acceptedInteractionTypeList.add(interactionType.getNumber());
//                        }
//                    }
//                });
//        Integer isSuportLink = 1;
//        if (runTimeContext.getRequestType() == RequestType.API_3_4_1
//                && (runTimeContext.getBaseRequest().getExt() == null || CollectionUtils.isEmpty(runTimeContext.getBaseRequest().getExt().getInteractSupportList()))) {
//            if (isSuportLink.equals(1)) {
//                acceptedInteractionTypeList.add(InteractionType.DEEPLINK_VALUE);
//            }
//            acceptedInteractionTypeList.add(InteractionType.DOWNLOAD_VALUE);
//            acceptedInteractionTypeList.add(InteractionType.OPEN_IN_APP_VALUE);
//            acceptedInteractionTypeList.add(InteractionType.WECHAT_MINI_PROGRAM_VALUE);
//        }
//        if (CollectionUtils.isNotEmpty(acceptedInteractionTypeList) && acceptedInteractionTypeList.contains(InteractionType.DEEPLINK.getNumber())) {
//            adSlotBuilder.setDeeplinkSupport(1);
//        }
        adSlotBuilder.addAllAcceptedInteractionTypes(acceptedInteractionTypeList);
        adSlotBuilder.addAllIncludeAdvertiserIds(runTimeContext.getIncludeAdvertiserIds());
        adSlotBuilder.addAllExcludeAdvertiserIds(runTimeContext.getExcludeAdvertiserIds());
        adSlotBuilder.addAllIncludeChannelIds(runTimeContext.getIncludeChannelIds());
        adSlotBuilder.addAllExcludeChannelIds(runTimeContext.getExcludeChannelIds());
        runTimeContext.setAcceptedInteractionTypeList(acceptedInteractionTypeList);
        //adSlotBuilder.putAllDspChannelForwardScare(runTimeContext.getDspChannelForwardScare());
        Version version11_3 = Version.newBuilder().setMajor(11).setMinor(3).build();

        //流量是否支持skan归因
        //1. ios应用级别 2.ios版本大于11.3 3.ios itunes id不为空
        if (null != runTimeContext.getAppModel() && SKAN_APP_LIST.get().contains(runTimeContext.getAppModel().getAppId())
                && WorkerUtils.versionCompare(bidRequestBuilder.getDevice().getOsVersion(), version11_3) >= 0
                && StringUtils.isNotEmpty(runTimeContext.getAppModel().getItunesId())) {
            adSlotBuilder.setSupportSkan(true);
        } else {
            adSlotBuilder.setSupportSkan(false);
        }

        //添加聚合ID
        try {
            String requestBundleId = runTimeContext.getRequestBundleId();
            int number = runTimeContext.getAdType().getNumber();
            List<String> collect = validTemplateModelList.stream().map(TemplateModel::getDcAspectRatio).distinct().collect(Collectors.toList());
            AdType adType = runTimeContext.getAdType();
            OsType osType = runTimeContext.getOsType();
            String appMixIdOri = requestBundleId.concat("_").concat(String.valueOf(osType.getNumber())).concat("_").concat(String.valueOf(number)).concat("_").concat(collect.get(0));
            LogUtils.logInfo(runTimeContext.getRequestId(),"appMixIdOri {}",appMixIdOri);
            String appMixId = DigestUtils.md5Hex(appMixIdOri).substring(8, 24);
            adSlotBuilder.setAppMixId(appMixId);
            runTimeContext.setAppMixId(appMixId);
            String appMixName;
            if (null != runTimeContext.getAppModel()) {
                appMixName = runTimeContext.getAppModel().getAppName().concat("_").concat(runTimeContext.getOsType().name().concat("_").concat(adType.name()).concat("_").concat(collect.get(0)));
            } else {
                appMixName = runTimeContext.getRequestBundleId().concat("_").concat(runTimeContext.getOsType().name().concat("_").concat(adType.name()).concat("_").concat(collect.get(0)));
            }
            adSlotBuilder.setAppMixName(appMixName);
            runTimeContext.setAppMixName(appMixName);
            LogUtils.logInfo(runTimeContext.getRequestId(),"appMixName {},appMixId {}",appMixName,appMixId);
        }catch (Exception e){
            LogUtils.logError(runTimeContext.getRequestId()," adxPolymerizationId error {}",e);
        }

        bidRequestBuilder.addAdslots(adSlotBuilder);
    }

    private void buildApp(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) {
        AppModel appModel = runTimeContext.getAppModel();
        if (appModel != null) {
            App.Builder appBuilder = App.newBuilder();
            if(null != runTimeContext.getPlacementModel()){
                appBuilder.setAppId(Optional.ofNullable(runTimeContext.getPlacementModel().getAppId()).orElse(""));
            }
            appBuilder.setName(Optional.ofNullable(appModel.getAppName()).orElse(""));
            appBuilder.setBundleId(Optional.ofNullable(appModel.getItunesId()).orElse(""));
            appBuilder.setAppCategory(Optional.ofNullable(appModel.getPrimaryIndustryId()).orElse(0));
            appBuilder.setSecondAppCategory(Optional.ofNullable(appModel.getSecondaryIndustryId()).orElse(0));
            String appModelBundle = Optional.ofNullable(appModel.getBundleId()).orElse("");
            if(runTimeContext.getAccountType() == 2){
                //adx流量
                appBuilder.setPackageName(appModelBundle);
            } else if (!StringUtils.isNumeric(appModelBundle)) {
                appBuilder.setPackageName(appModelBundle);
            } else if (!StringUtils.isNumeric(runTimeContext.getRequestBundleId())) {
                appBuilder.setPackageName(runTimeContext.getRequestBundleId());
            } else {
                appBuilder.setPackageName(appModelBundle);
            }
            appBuilder.setStoreUrl(Optional.ofNullable(appModel.getAppMarketUrl()).orElse(""));
            //百度
            if (runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                if (appModel.getPlatformId() == 1) { //安卓
                    appBuilder.setPackageName(Optional.ofNullable(runTimeContext.getBaseRequest().getApp().getBundle()).orElse(appBuilder.getPackageName()));
                } else {
                    appBuilder.setBundleId(Optional.ofNullable(runTimeContext.getBaseRequest().getApp().getBundle()).orElse(appBuilder.getBundleId()));
                }
            }
            switch (IsPaidApp.forNumber(Optional.ofNullable(appModel.getIsPaidApp()).orElse(0))) {
                case FREE:
                    appBuilder.setIsPaidApp(false);
                    break;
                case PAID:
                    appBuilder.setIsPaidApp(true);
                    break;
                default:
                    appBuilder.setIsPaidApp(false);
                    break;
            }
            bidRequestBuilder.setApp(appBuilder);
        } else {
            //adx 流量未获取到appModel
            App.Builder appBuilder = App.newBuilder();
            appBuilder.setPackageName(Optional.ofNullable(runTimeContext.getRequestBundleId()).orElse(""));
            if(null != runTimeContext.getBaseRequest()){
                appBuilder.setName(Optional.of(runTimeContext.getBaseRequest().getApp().getName()).orElse(""));
            }
            //百度
            if (null != runTimeContext.getPlacementModel() && runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                if (OsType.ANDROID.equals(runTimeContext.getOsType())) { //安卓
                    appBuilder.setPackageName(StringUtils.isNotBlank(runTimeContext.getBaseRequest().getApp().getBundle())?runTimeContext.getBaseRequest().getApp().getBundle():appBuilder.getPackageName());
                } else {
                    appBuilder.setBundleId(StringUtils.isNotBlank(runTimeContext.getBaseRequest().getApp().getBundle())?runTimeContext.getBaseRequest().getApp().getBundle():"");
                }
            }
            if(null != runTimeContext.getPlacementModel()){
                appBuilder.setAppId(Optional.ofNullable(runTimeContext.getPlacementModel().getAppId()).orElse(""));
            }
            bidRequestBuilder.setApp(appBuilder);
        }
        if (Objects.nonNull(runTimeContext.getAppVersionMajor())) {
            Version.Builder versionBuilder = Version.newBuilder();
            versionBuilder
                    .setMajor(Optional.ofNullable(runTimeContext.getAppVersionMajor()).orElse(0))
                    .setMinor(Optional.ofNullable(runTimeContext.getAppVersionMinor()).orElse(0))
                    .setMicro(Optional.ofNullable(runTimeContext.getAppVersionMicro()).orElse(0));
            App.Builder appBuilder = bidRequestBuilder.getAppBuilder();
            appBuilder.setVersion(versionBuilder.build());
        }
    }

    private void buildDevice(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) {
        Device device = runTimeContext.getDevice();
        if (device != null) {
            cn.inmobi.pb.api.Device.Builder deviceBuilder = cn.inmobi.pb.api.Device.newBuilder();
            if (device.getId() != null) {
                deviceBuilder.setDid(DeviceId.newBuilder()
                        .setImbId(Optional.ofNullable(device.getId().getInmobi_id()).orElse(""))
                         .setIdfa(Optional.ofNullable(device.getId().getIdfa()).orElse(""))
                        .setIdfv(Optional.ofNullable(device.getId().getIdfv()).orElse(""))
                        .setImei(Optional.ofNullable(device.getId().getImei()).orElse(""))
                        .setSsid(Optional.ofNullable(device.getId().getSsid()).orElse(""))
                        .setWifiMac(Optional.ofNullable(device.getId().getSsid()).orElse(""))
                        .setDsid(Optional.ofNullable(device.getId().getDsid()).orElse(""))
                        .setOpenUdid(Optional.ofNullable(device.getId().getOpenUdid()).orElse(""))
                        .setImeiMd5(Optional.ofNullable(device.getId().getImeiMd5()).orElse(""))
                        .setAndroidId(Optional.ofNullable(device.getId().getAndroidId()).orElse(""))
                        .setUuid(Optional.ofNullable(device.getId().getUuid()).orElse(""))
                        .setOaid(Optional.ofNullable(device.getId().getOaid()).orElse(""))
                        .setAndroidIdMd5(Optional.ofNullable(device.getId().getUm5()).orElse(""))
                        .setAndroidIdSha1(Optional.ofNullable(device.getId().getO1()).orElse(""))
                        .setIdfaMd5(Optional.ofNullable(device.getId().getIdfaMd5()).orElse(""))
                        .setOaidMd5(Optional.ofNullable(device.getId().getOaidMd5()).orElse(""))
                        .setPaid(Optional.ofNullable(device.getId().getPaid()).orElse(""))
                        .setPaid(Optional.ofNullable(device.getId().getPaid()).orElse(""))
                        .setMac(Optional.ofNullable(device.getId().getMac()).orElse(""))
                        .setMacMd5(Optional.ofNullable(device.getId().getMacMd5()).orElse("")));
            }
            if (StringUtils.isNotBlank(device.getId().getIdfa())&& !device.getId().getIdfa().equals(IOS_ZERO_IDFA) && StringUtils.isBlank(device.getId().getIdfaMd5())){
                deviceBuilder.setDid(deviceBuilder.getDidBuilder().setIdfaMd5(DigestUtils.md5Hex(device.getId().getIdfa())));
            }
            if (StringUtils.isNotBlank(device.getId().getOaid()) && StringUtils.isBlank(device.getId().getOaidMd5())){
                deviceBuilder.setDid(deviceBuilder.getDidBuilder().setOaidMd5(DigestUtils.md5Hex(device.getId().getOaid())));
            }
            deviceBuilder.setDeviceType((Optional.ofNullable(device.getDeviceType()).orElse(DeviceType.UNKNOW_DEVICE_TYPE)).getNumber());
            deviceBuilder.setOsType((Optional.ofNullable(device.getOsType()).orElse(OsType.UNKNOW_OS_TYPE)).getNumber());
            if(null != device.getDeviceOrientation()){
                deviceBuilder.setOrientation(device.getDeviceOrientation());
            }
            if (StringUtils.isNotBlank(device.getOsVersion())) {
                String[] versionSegments = StringUtils.split(device.getOsVersion(), ".");
                if (ArrayUtils.isNotEmpty(versionSegments)) {
                    Version.Builder versionBuilder = Version.newBuilder();
                    try {
                        if (versionSegments.length >= 3) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                            versionBuilder.setMinor(Optional.ofNullable(Integer.valueOf(versionSegments[1])).orElse(0));
                            versionBuilder.setMicro(Optional.ofNullable(Integer.valueOf(versionSegments[2])).orElse(0));
                        } else if (versionSegments.length == 2) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                            versionBuilder.setMinor(Optional.ofNullable(Integer.valueOf(versionSegments[1])).orElse(0));
                        } else if (versionSegments.length == 1) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                        }
                    } catch (Exception e) {
                        //do nothing
                    }
                    deviceBuilder.setOsVersion(versionBuilder);
                }
            }
            deviceBuilder.setVendor(Optional.ofNullable(device.getVendor()).orElse(""));
            deviceBuilder.setModel(Optional.ofNullable(device.getModel()).orElse(""));

            //todo
            deviceBuilder.setVendorSource(1);
            deviceBuilder.setBootMark(Optional.ofNullable(device.getBootMark()).orElse(""));
            deviceBuilder.setUpdateMark(Optional.ofNullable(device.getUpdateMark()).orElse(""));
            deviceBuilder.setConnType((Optional.ofNullable(device.getConnectType()).orElse(ConnectionType.UNKNOW_CONNECTION_TYPE)).getNumber());
            deviceBuilder.setConnTypeSource(device.getConnTypeSource());
            if (device.getDeviceSize() != null) {
                deviceBuilder.setScreenSize(Size.newBuilder()
                        .setWidth(device.getDeviceSize().getWidth())
                        .setHeight(device.getDeviceSize().getHeight()));
            }
            deviceBuilder.setCarrier((Optional.ofNullable(device.getCarrier()).orElse(Carrier.UNKNOW_CARRIER)).getNumber());
            deviceBuilder.setCarrierSource(device.getCarrierSource());
            if(null != device.getDeviceDensity()){
                deviceBuilder.setPpi(device.getDeviceDensity());
            }
            deviceBuilder.setLanguage(Optional.ofNullable(device.getLanguage()).orElse(""));
            //    private String powerOnTime; //开机时间
            //    private String osUpdateTime; //版本更新时间
            //    private String phoneName; //手机名称
            //    String caid;
            //    double memorySize; //运行时内存（单位Byte）
            //    String modelCode; //apple mode code，例如D22AP
            //    int diskSize; // 磁盘大小（单位Byte）
            //    String deviceMachine;
            //    String timeZone;
            if (device.getCaidModel() != null) {
                deviceBuilder.setPowerOnTime(Optional.ofNullable(device.getCaidModel().getBootTimeSecond()).orElse(""));
                deviceBuilder.setOsUpdateTimeSecond(Optional.ofNullable(device.getCaidModel().getOsUpdateTimeSecond()).orElse(""));
                deviceBuilder.setPhoneName(Optional.ofNullable(device.getCaidModel().getPhoneNameMd5()).orElse(""));
                //deviceBuilder.setCaid(Optional.ofNullable(device.getCaidModel().getCaid()).orElse(""));
                deviceBuilder.setMemorySize(device.getCaidModel().getMemorySize());
                deviceBuilder.setModelCode(Optional.ofNullable(device.getCaidModel().getModelCode()).orElse(""));
                deviceBuilder.setDiskSize((long) (Optional.ofNullable(device.getCaidModel().getDiskSize()).orElse(0D).doubleValue()));
                deviceBuilder.setDeviceMachine(Optional.ofNullable(device.getCaidModel().getDeviceMachine()).orElse(""));
                deviceBuilder.setTimeZone(Optional.ofNullable(device.getCaidModel().getTimeZone()).orElse(""));
                deviceBuilder.setLanguage(Optional.ofNullable(device.getCaidModel().getLanguage()).orElse(""));
                deviceBuilder.setCountry(Optional.ofNullable(device.getCaidModel().getCountry()).orElse(""));
                deviceBuilder.setOsv(Optional.ofNullable(device.getCaidModel().getOsv()).orElse(""));
                deviceBuilder.setCarrierCaid(Optional.ofNullable(device.getCaidModel().getCarrier()).orElse(""));
            }
            LogUtils.logInfo(runTimeContext.getRequestId(), "runTimeContext caid   {}  {}",runTimeContext.getCaid(), runTimeContext.getCaidVersion());

            if(device.getCaidModel() != null){
                CaidModel caidModel = device.getCaidModel();
                String caid = caidModel.getCaid();
                String caidVersion = caidModel.getCaidVersion();
                String preCaid = caidModel.getPreCaid();
                String preCaidVersion = caidModel.getPreCaidVersion();

                deviceBuilder.setCaid(Optional.ofNullable(caid).orElse(""));
                deviceBuilder.setCaidVersion(Optional.ofNullable(caidVersion).orElse(""));
                deviceBuilder.setPreCaid(Optional.ofNullable(preCaid).orElse(""));
                deviceBuilder.setPreCaidVersion(Optional.ofNullable(preCaidVersion).orElse(""));

            }
            if (runTimeContext.getRequestType().isSdk()) {
                //添加hms 参数
                String hmsCoreVersion = runTimeContext.getSdkRequest().get(SdkParam.HMS_CORE_VERSION.of());
                String agVersion = runTimeContext.getSdkRequest().get(SdkParam.AG_VERSION.of());
                if(StringUtils.isNotBlank(hmsCoreVersion)) deviceBuilder.setHmsCoreVersion(hmsCoreVersion);
                if(StringUtils.isNotBlank(agVersion)) deviceBuilder.setAgVersion(agVersion);
            }else{
                BaseRequest baseRequest = runTimeContext.getBaseRequest();
                String hmsVerCode = baseRequest.getDevice().getHmsVerCode();
                String romVersion = baseRequest.getDevice().getRomVersion();
                String appStoreVersion = baseRequest.getDevice().getAppStoreVersion();
                if(StringUtils.isNotBlank(hmsVerCode)) deviceBuilder.setHmsCoreVersion(hmsVerCode);
                if(StringUtils.isNotBlank(romVersion)) deviceBuilder.setRomVersion(romVersion);
                if(StringUtils.isNotBlank(appStoreVersion)) deviceBuilder.setAppStoreVersion(appStoreVersion);
                deviceBuilder.setDpi(baseRequest.getDevice().getDpi());
            }
            bidRequestBuilder.setDevice(deviceBuilder);
        }
    }

    /**
     * error 情况下构建默认的bidRequest
     * 这个方法跟脆弱，修改需发MR给Remi!
     *
     * @param runTimeContext
     * @return
     * @throws SunException
     */
    public BidRequest buildDefaultBidRequest(RunTimeContext runTimeContext) {
        PerfUtils.perf("buildDefaultBidRequest");
        BidRequest.Builder bidRequestBuilder = BidRequest.newBuilder();
        bidRequestBuilder.setRequestId(Optional.ofNullable(runTimeContext.getRequestId()).orElse(""));
        RequestType requestType = runTimeContext.getRequestType();

        if (requestType != null) {
            if (requestType.isSdk()) {
                bidRequestBuilder.setSdkVersion(StringUtils.isNotBlank(runTimeContext.getSdkVersion()) ? runTimeContext.getSdkVersion() : "");
            } else {
                bidRequestBuilder.setApiVersion(Optional.ofNullable(requestType.getVersion()).orElse(Version.getDefaultInstance()));
            }
        } else {
            log.info("runTimeContext.getTermCodes={}", runTimeContext.getTermCodes());
            PerfUtils.perf("buildDefaultBidRequest.requestTypeIsNull");
        }
        if (requestType == null) {
            bidRequestBuilder.setIntegrationType(IntegrationType.UNKNOW_INTEGRATION_TYPE_VALUE);
        } else {
            if (requestType.isSdk()) {
                bidRequestBuilder.setIntegrationType(IntegrationType.SDK_VALUE);
            } else {
                bidRequestBuilder.setIntegrationType(IntegrationType.API_VALUE);
            }
        }

        bidRequestBuilder.setUa(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""));
        //TODO make sure which ip to be choose
        if (StringUtils.isNotBlank(runTimeContext.getUserIpV4())) {
            bidRequestBuilder.setIp(runTimeContext.getUserIpV4());
        } else if (StringUtils.isNotBlank(runTimeContext.getUserIpV6())) {
            bidRequestBuilder.setIpv6(runTimeContext.getUserIpV6());
        }

        bidRequestBuilder.setGeo(Optional.ofNullable(runTimeContext.getGeo()).orElse(Geo.getDefaultInstance()));
        if (ObjectUtils.isNotEmpty(runTimeContext.getEarthResponse())){
            bidRequestBuilder.setNappScore(runTimeContext.getEarthResponse().getScore());
            bidRequestBuilder.setTencentScore(runTimeContext.getEarthResponse().getTencentScore());
            bidRequestBuilder.setAppFrequency(runTimeContext.getEarthResponse().getAppFrequency());
        }
        buildUser(runTimeContext, bidRequestBuilder);
        buildApp(runTimeContext, bidRequestBuilder);
        buildDevice(runTimeContext, bidRequestBuilder);
        buildDefaultAdSlot(runTimeContext, bidRequestBuilder);
        setTestFlag(runTimeContext, bidRequestBuilder);
        bidRequestBuilder.setLat(runTimeContext.getLat());
        bidRequestBuilder.setFloor(runTimeContext.getBaiduBrice());
        bidRequestBuilder.setAdxFlag(runTimeContext.getAccountType());
        return bidRequestBuilder.build();
    }

    private void buildDefaultAdSlot(RunTimeContext runTimeContext, BidRequest.Builder bidRequestBuilder) {
        RequestType requestType = runTimeContext.getRequestType();
        AdSlot.Builder adSlotBuilder = AdSlot.newBuilder();
        //only one adSlot
        if (runTimeContext.isSecure()) {
            adSlotBuilder.setIsSecure(1);
        } else {
            adSlotBuilder.setIsSecure(0);
        }
        adSlotBuilder.setId(String.valueOf(Optional.ofNullable(runTimeContext.getPlacementId()).orElse(0L))); // AdSlot.id is i64
        if (StringUtils.isBlank(adSlotBuilder.getId()) || adSlotBuilder.getId().equals("0")) {
            PerfUtils.perf("buildDefaultBidRequest.placementIdIsEmpty");
        }
        if (runTimeContext.getPlacementModel() != null) {
            bidRequestBuilder.setPubAccountId(StringUtils.isNotBlank(runTimeContext.getPlacementModel().getAccountId()) ? runTimeContext.getPlacementModel().getAccountId() : "");
        }
        if (StringUtils.isBlank(bidRequestBuilder.getPubAccountId())) {
            PerfUtils.perf("buildDefaultBidRequest.appModelIsNull");
        }

        adSlotBuilder.setAdType((Optional.ofNullable(runTimeContext.getAdType()).orElse(AdType.UNKNOW_AD_TYPE)).getNumber());
        adSlotBuilder.putAllFloorCpm(runTimeContext.getFloorEntityMap().entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().getNumber(), Map.Entry::getValue, (o1, o2) -> o1)));
        List<CreativeTypeRatio> acceptedCreativeTypeRatioList = new ArrayList<>();
        if (runTimeContext.getPlacementModel() != null) {
            List<TemplateModel> validTemplateModelList = runTimeContext.getPlacementModel().getTemplateList();
            PlacementCustomize placementCustomize = runTimeContext.getPlacementModel().getPlacementCustomize();
            for (TemplateModel templateModel : validTemplateModelList) {
                if (templateModel != null && requestType != null) {
                    if (!requestType.isSdk()
                            && runTimeContext.getBaseRequest() != null
                            && CollectionUtils.isNotEmpty(runTimeContext.getBaseRequest().getExt().getCreativeSupportList())) {
                        //请求中指定要的创意类型
                        if (!runTimeContext.getBaseRequest().getExt().getCreativeSupportList().contains(templateModel.getDcCreativeTypeId())) {
                            continue;
                        }
                    }
                    if (templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE && !requestType.isSupportJsonVideo()) {
                        //filter those template when request don't support JsonVideo creativeType
                        continue;
                    }
                    //check placement customize param
                    if (placementCustomize != null) {
                        if (runTimeContext.getDevice() != null) {
                            ConnectionType connectionType = Optional.ofNullable(runTimeContext.getDevice().getConnectType()).orElse(ConnectionType.UNKNOW_CONNECTION_TYPE);
                            if (placementCustomize.getIsStaticWifi() == 0) {
                                //not support static ad when network is wifi
                                if (connectionType == ConnectionType.WIFI
                                        && (templateModel.getDcCreativeTypeId() == CreativeType.IMAGE_VALUE
                                        || templateModel.getDcCreativeTypeId() == CreativeType.GIF_VALUE)) {
                                    continue;
                                }
                            }

                            if (placementCustomize.getIsStaticNonWifi() == 0) {
                                //not support static ad when network is not wifi
                                if (connectionType != ConnectionType.WIFI
                                        && (templateModel.getDcCreativeTypeId() == CreativeType.IMAGE_VALUE
                                        || templateModel.getDcCreativeTypeId() == CreativeType.GIF_VALUE)) {
                                    continue;
                                }
                            }

                            if (placementCustomize.getIsVideoWifi() == 0) {
                                //not support video ad when network is wifi
                                if (connectionType == ConnectionType.WIFI
                                        && templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                                    continue;
                                }
                            }

                            if (placementCustomize.getIsVideoNonWifi() == 0) {
                                //not support video ad when network is not wifi
                                if (connectionType != ConnectionType.WIFI
                                        && templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                                    continue;
                                }
                            }
                        }
                    }
                    CreativeTypeRatio.Builder builder = CreativeTypeRatio.newBuilder()
                            .setCreativeType(templateModel.getDcCreativeTypeId())
                            .setWhRatio(templateModel.getDcAspectRatio());
                    if (templateModel.getDcCreativeTypeId() == CreativeType.VIDEO_VALUE) {
                        builder.setMaxVideoDuration(templateModel.getVideoDuration());
                    }
                    if (runTimeContext.getAdType() == AdType.FEED) {
                        builder.setFeedLayout(templateModel.getCustomFeedStyle());
                    } else {
                        builder.setFeedLayout(TemplateLayOut.UNKNOW_TEMPLATE_DISPLAY_VALUE);
                    }
                    acceptedCreativeTypeRatioList.add(builder.build());
                    adSlotBuilder.addCreativeSupport(templateModel.getDcCreativeTypeId());
                }
            }
            if (placementCustomize != null) {
                //adSlotBuilder.setDeeplinkSupport(Optional.ofNullable(placementCustomize.getSupportDeeplink()).orElse(1));
            }
        }

        if (requestType != null && !requestType.isSdk()) {
            if (runTimeContext.getBaseRequest() != null
                    && CollectionUtils.isNotEmpty(runTimeContext.getBaseRequest().getExt().getCreativeSupportList())) {
                //如果请求携带，使用请求中的数据进行覆盖
                adSlotBuilder.clearCreativeSupport().addAllCreativeSupport(runTimeContext.getBaseRequest().getExt().getCreativeSupportList());
            }
        }

        if (CollectionUtils.isNotEmpty(acceptedCreativeTypeRatioList)) {
            adSlotBuilder.addAllAcceptedCreativeTypeRatios(acceptedCreativeTypeRatioList);
        }
        List<Integer> acceptedInteractionTypeList = new ArrayList<>();
        Arrays.stream(InteractionType.values())
                .filter(interactionType -> interactionType != UNRECOGNIZED
                        && interactionType != NO_INTERACTION
                        && interactionType != UNKNOW_INTERACTION_TYPE)
                .forEach(interactionType -> {
                    Integer isSuportLink = 1;
                    if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getPlacementCustomize() != null && runTimeContext.getPlacementModel().getPlacementCustomize().getSupportDeeplink() != null) {
//                        isSuportLink = runTimeContext.getPlacementModel().getPlacementCustomize().getSupportDeeplink();
                    }
                    if (interactionType == InteractionType.DEEPLINK) {
                        if ((Optional.ofNullable(requestType).orElse(RequestType.IN_VALID)).isSdk()) {
                            //sdk always support deeplink
                            if (isSuportLink.equals(1)) {
                                acceptedInteractionTypeList.add(interactionType.getNumber());
                            }
                        } else {
                            boolean isSupportDeeplink = true;
                            if (runTimeContext.getBaseRequest() != null && runTimeContext.getBaseRequest().getExt() != null) {
                                if (runTimeContext.getRequestType() == RequestType.API_3_1) {
                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getExternalSupported();

                                } else if (runTimeContext.getRequestType() == RequestType.API_3_4_1) {
                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getInteractSupportList().contains(3);
                                } else {
                                    isSupportDeeplink = runTimeContext.getBaseRequest().getExt().getSupportDeeplink();
                                }
                            }

                            if (isSupportDeeplink && (isSuportLink.equals(1))) {
                                //api support deeplink only when SupportDeeplink param is true
                                acceptedInteractionTypeList.add(interactionType.getNumber());
                            }
                        }
                    } else {
                        acceptedInteractionTypeList.add(interactionType.getNumber());
                    }
                });
        if (CollectionUtils.isNotEmpty(acceptedInteractionTypeList) && acceptedInteractionTypeList.contains(InteractionType.DEEPLINK.getNumber())) {
            adSlotBuilder.setDeeplinkSupport(1);
        }
        adSlotBuilder.addAllAcceptedInteractionTypes(acceptedInteractionTypeList);
        adSlotBuilder.addAllIncludeAdvertiserIds(runTimeContext.getIncludeAdvertiserIds());
        adSlotBuilder.addAllExcludeAdvertiserIds(runTimeContext.getExcludeAdvertiserIds());
        adSlotBuilder.addAllIncludeChannelIds(runTimeContext.getIncludeChannelIds());
        adSlotBuilder.addAllExcludeChannelIds(runTimeContext.getExcludeChannelIds());
        runTimeContext.setAcceptedInteractionTypeList(acceptedInteractionTypeList);
        bidRequestBuilder.addAdslots(adSlotBuilder);
    }

    public static void main(String[] args) {
        RunTimeContext runTimeContext = new RunTimeContext();
        runTimeContext.setBaseRequest(BaseRequest.newBuilder().getDefaultInstanceForType());
        String bundle = runTimeContext.getBaseRequest().getApp().getBundle().equals("")?"1":"2";
        System.out.println(bundle);
    }
}