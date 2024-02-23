package com.inmobi.supply.sun.service.postProc.auction.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.model.redis.AppInfoBaseModel;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import cn.inmobi.pb.api.*;
import com.alibaba.fastjson.JSONObject;
import com.inmobi.supply.framework.common.utils.log.RateLogger;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicMapConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.BidDropReason;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.constant.SaturnDsp;
import com.inmobi.supply.sun.model.material.MaterialInfo;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.CreativeAuditRepo;
import com.inmobi.supply.sun.repo.file.MaterialInfoRepo;
import com.inmobi.supply.sun.repo.redis.service.AppInfoBaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.inmobi.supply.sun.utils.SunStringUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.CREATIVE_REPEAT_PUB_LIST;
import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.CREATIVE_REPEAT_MAPPING;
import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.PLACEMENT_SPLIT_MAPPING;
import static com.inmobi.supply.sun.constant.RuntimeConstant.ONE_POINT_ZERO_SIX;
import static com.inmobi.supply.sun.constant.SaturnDsp.GDT_CUSTOM;

/**
 * check BidResponse of adPool
 *
 * @author remi.li
 */
@Service
@Slf4j
public class CheckBidResponse extends LogUtils {
    @Autowired
    CreativeAuditRepo creativeAuditRepo;

    @Autowired
    MaterialInfoRepo materialInfoRepo;

    @Autowired
    private AppInfoBaseRepo appInfoBaseRepo;



    public BidDropReason checkBidResponse(RunTimeContext runTimeContext, BidResponse bidResponse) {


        if (bidResponse == null || bidResponse.equals(BidResponse.getDefaultInstance())) {
            logInfo(runTimeContext.getRequestId(),"bidResponse empty! {} placementId {}", bidResponse, runTimeContext.getPlacementId());
            return BidDropReason.BID_EMPTY;
        }
        //runTimeContext.getAllBidResponseList().add(bidResponse);

        PerfUtils.perf("adPoolResponse",
                Tag.of("sourceType", Optional.ofNullable(SourceType.forNumber(bidResponse.getSourceType())).orElse(SourceType.UNKONW_SOURCE_TYPE).name()),
                Tag.of("errorCode", String.valueOf(bidResponse.getErrorCode())));
        if (StringUtils.isBlank(bidResponse.getRequestId())
                || CollectionUtils.isEmpty(bidResponse.getAdsList())) {
            logInfo(runTimeContext.getRequestId(),"bidResponse ad empty! {} placementId {}", bidResponse, runTimeContext.getPlacementId());
            return BidDropReason.AD_EMPTY;
        }
        //check sourceType & saturnDsp
        SourceType sourceType = SourceType.forNumber(bidResponse.getSourceType());
        FloorEntityType floorEntityType = FloorEntityType.forNumber(bidResponse.getCustomRevShare());
        if (sourceType == null
                || floorEntityType == null
                || sourceType == SourceType.UNKONW_SOURCE_TYPE
                || floorEntityType == FloorEntityType.UNKNOW_FLOOR_ENTITY_TYPE) {
            logInfo(runTimeContext.getRequestId(),"placementId {} error by sourceType / floorEntityType", runTimeContext.getPlacementId());
            return BidDropReason.SOURCE_TYPE_ERROR;
        }
        String sourceTypeStr = String.valueOf(bidResponse.getSourceType());
        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.AUCTION_FAILD_VALUE);

        //     String creativeAuditPubAdvIdJsonStr = DynamicStringConfig.CREATIVE_AUDIT_PUB_ADV_ID.get();

        Map<FloorEntityType, Long> floorEntityTypeLongMap = runTimeContext.getFloorEntityMap();

////        log.info("requestid={} validAdList={} placementId={} ducation={} model={} model2={}",
//                runTimeContext.getRequestId(),
//                bidResponse,
//                runTimeContext.getPlacementId(),
//                ObjectMapperUtils.toJSON(DynamicListConfig.DUCATION_PLACEMENT_LIST.get()),
//                ObjectMapperUtils.toJSON(DynamicMapConfig.DUCATION_PLACEMENT_MODEL.get())
//        );

        if (CollectionUtils.isNotEmpty(bidResponse.getAdsList())) {
            runTimeContext.getOriginBidResponseList().add(bidResponse);
        }
        BidResponse finalBidResponse = bidResponse;
        logInfo(runTimeContext.getRequestId(),"valid BidResponse={} placementId={}", bidResponse, runTimeContext.getPlacementId());
        List<Ad> validAdList = bidResponse.getAdsList().stream()
                .filter(ad -> {
                    if (ad.getBidPrice() <= 0) {
                        PerfUtils.perf("invalidAd", Tag.of("reason", "bidPrice <= 0"));
                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.BID_PRICE_LOW_ZERO_VALUE);
                        return false;
                    }
                    if (ad.getBidPriceCpc() > 0L && ad.getBidType() == 1) {
                        runTimeContext.getDspMap().put(sourceTypeStr, 19);
                        return false;
                    }
                    if (ad.getAdvBidPriceOcpm() > 0L && ad.getBidType() != 3) {
                        PerfUtils.perf("sun.ocpm.invalidAd", Tag.of("reason", "20"));
                        runTimeContext.getDspMap().put(sourceTypeStr, 20);
                        return false;
                    }
                    if (ad.getAdvBidPriceOcpm() == 0L && ad.getBidType() == 3) {
                        PerfUtils.perf("sun.ocpm.invalidAd", Tag.of("reason", "21"));
                        runTimeContext.getDspMap().put(sourceTypeStr, 21);
                        return false;
                    }

                    if (ad.getBidPriceCpc() > 0L) {
                        return true;
                    }

                    if (runTimeContext.getSwitchFlag() == 1) {
                        PerfUtils.perf("sun.mkt.place.count", "status", "checkResponse");
                        try{
                            // 判断marketPlace adv_bid_price 是否大于 floor
                            if(ad.getAdvBidPrice() < floorEntityTypeLongMap.get(floorEntityType)){
                                PerfUtils.perf("invalidAd", Tag.of("reason", "advBidPrice less than floor price"));
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.BID_PRICE_LOW_FLOOR_VALUE);
                                return false;
                            }
                        }catch (Exception e){
                            LogUtils.logError(runTimeContext.getRequestId(),"checkBidResponseError {}",e);
                            return false;
                        }
                    } else {
                        // 4项还原再去掉12项折扣 判断一价扣完12项之后，是否大于floor
                        double adpoolDeduction = ad.getAdpoolDeduction();
                        double netDeduction = ad.getNetDeduction();
                        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP));
                        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
                        BigDecimal price4 = BigDecimal.valueOf(ad.getBidPrice()).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
                        BigDecimal price12 = price4.multiply(twelveDeduction);
                        if (price12.longValue() <= floorEntityTypeLongMap.get(floorEntityType)) {
                            runTimeContext.getLowerThanFloorBidResponseList().add(finalBidResponse);
                            PerfUtils.perf("invalidAd", Tag.of("reason", "bidPrice less than floor price"));
                            log.info("deduction drop requestId={} bidResponse={} placementId={} fourDeduction={} twelveDeduction={} price4={} price12={} floor={}",
                                    runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId(),
                                    fourDeduction, twelveDeduction, price4, price12, floorEntityTypeLongMap.get(floorEntityType));
                            runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.BID_PRICE_LOW_FLOOR_VALUE);
                            return false;
                        }
                    }

//                    } else {
//                        log.info("requestid={}  type=old ad={} floor={}",
//                                runTimeContext.getRequestId(), ad, floorEntityTypeLongMap.get(floorEntityType));
//
//                        if (ad.getBidPrice() <= floorEntityTypeLongMap.get(floorEntityType)) {
//                            runTimeContext.getLowerThanFloorBidResponseList().add(bidResponse);
//                            PerfUtils.perf("invalidAd", Tag.of("reason", "bidPrice less than floor price"));
//                            return false;
//                        }
//                    }
                    // 数据漏斗功能

                    if (finalBidResponse.getSourceType() == SourceType.SATURN_VALUE && StringUtils.isNotBlank(ad.getSaturnSegmentId()) && StringUtils.isNotBlank(ad.getSaturnSegmentId())) {
                        String key = ad.getSaturnChannelId() + "_" + ad.getSaturnSegmentId();
                        runTimeContext.getBiddingAfterFilter().put(key, ad.getBidPrice());
                    }
                    if (finalBidResponse.getSourceType() != SourceType.SATURN_VALUE) {
                        String key = ad.getAdvAccId() + "_" + ad.getCampId() + "_" + ad.getGroupId();
                        runTimeContext.getBiddingAfterFilter().put(key, ad.getBidPrice());
                    }

                    if (ad.getCreative() == null
                            || StringUtils.isBlank(ad.getAdId())) {
                        PerfUtils.perf("invalidAd", Tag.of("reason", "creative or adId is null"));
                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.CREATIVE_ADID_NULL_VALUE);
                        log.info("creative drop requestId={} bidResponse={} placementId={}",
                                runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                        return false;
                    }
                    //check materialMeta
                    MaterialMeta materialMeta = ad.getCreative();
                    CreativeType creativeType = CreativeType.forNumber(materialMeta.getCreativeType());
                    InteractionType interactionType = InteractionType.forNumber(materialMeta.getInteractionType());
                    if (creativeType == null
                            || creativeType == CreativeType.UNKNOW_CREATIVE_TYPE
                            || interactionType == null
                            || interactionType == InteractionType.UNKNOW_INTERACTION_TYPE) {
                        //in M0, only support deeplink + download ad
//                        log.info("requestid {} channelId {} error by creativeType / interactionType", runTimeContext.getRequestId(), channelId);
                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.CREATIVE_INTERATION_ERROR_VALUE);
                        PerfUtils.perf("invalidAd", Tag.of("reason", "error by creativeType / interactionType"));
                        log.info("creativeTypeOrInteractionType drop requestId={} bidResponse={} placementId={}",
                                runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                        return false;
                    }
                    if (!runTimeContext.getPlacementCreativeTypes().contains(creativeType)) {
                        //返回creativeType不包含在请求的creativeType之中
//                        log.info("requestid {} channelId {} error by PlacementCreativeTypes", runTimeContext.getRequestId(), channelId);
                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.PLACEMENT_CREATIVE_TYPES_ERROR_VALUE);
                        PerfUtils.perf("invalidAd", Tag.of("reason", "error by PlacementCreativeTypes"));
                        log.info("unSupportCreative drop requestId={} bidResponse={} placementId={}",
                                runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                        return false;
                    }
                    if (creativeType == CreativeType.VIDEO && !runTimeContext.getRequestType().isSupportJsonVideo()) {
                        //不支持JsonVideo的RequestType需要过滤
                        PerfUtils.perf("invalidAd", Tag.of("reason", "error by PlacementCreativeTypes"));
                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.VIDEO_SUPPORT_JSON_VIDEO_ERROR_VALUE);
                        log.info("unSupportCreativeVideo drop requestId={} bidResponse={} placementId={}",
                                runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                        return false;
                    }
                    //check by creativeType
                    switch (creativeType) {
                        case IMAGE:
                            if (materialMeta.getImage() == null || StringUtils.isBlank(materialMeta.getImage().getUrl())
                                    || materialMeta.getImage().getWidth() == 0
                                    || materialMeta.getImage().getHeight() == 0) {
//                                log.info("requestid {} channelId {} error by creativeType image", runTimeContext.getRequestId(), channelId);
//                                tmp.toBuilder().setAuctionStatus(DspStaus.);
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.IMAGE_CREATIVE_TYPE_ERROR_VALUE);

                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by creativeType image"));
                                log.info("imageCreativeField drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                            break;
                        case VIDEO:
                            if (materialMeta.getVideo() == null
                                    || StringUtils.isBlank(materialMeta.getVideo().getVideoUrl())) {
//                                log.info("requestid {} channelId {} error by creativeType video", runTimeContext.getRequestId(), channelId);
//                                tmp.toBuilder().setAuctionStatus(DspStaus.);
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.VIDEO_CREATIVE_TYPE_ERROR_VALUE);
                                log.info("videoCreativeField drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by creativeType video"));
                                return false;
                            }
                            break;
                    }
                    //check by interactionType
                    switch (interactionType) {
                        case DEEPLINK:
                            if (!ad.getSaturnChannelId().equals(GDT_CUSTOM)
                                    && (StringUtils.isBlank(materialMeta.getTargetUrl())
                                    || StringUtils.isBlank(materialMeta.getDeeplinkUrl()))) {
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.INTERACTION_TYPE_DEEPLINK_ERROR_VALUE);
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by interactionType deeplink"));
                                log.info("DEEPLINK drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                            break;
                        case DOWNLOAD:
                            if (!ad.getSaturnChannelId().equals(GDT_CUSTOM) && StringUtils.isBlank(materialMeta.getDownloadUrl())) {
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.INTERACTION_TYPE_DOWNLOAD_ERROR_VALUE);
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by interactionType Download"));
                                log.info("DOWNLOAD drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }

                            //强制六要素校验
                            if (DynamicListConfig.PUB_APP_INFO_VALID_LIST.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                                AppInfoBaseModel appInfoBaseModel = null;
                                String packageName = materialMeta.getPackageName();
                                OsType osType = runTimeContext.getDevice().getOsType();
                                if (StringUtils.isNotEmpty(packageName)) {
                                    appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPackageName(packageName, osType.getNumber());
                                }
                                if (Objects.isNull(appInfoBaseModel) && runTimeContext.getWinBidResponse().getSourceType()==SourceType.SATURN_VALUE) {
                                    appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPublisherId(ad.getSaturnChannelId(), osType.getNumber());
                                }
                                if (Objects.isNull(appInfoBaseModel) && runTimeContext.getWinBidResponse().getSourceType()==SourceType.PERF_VALUE) {
                                    appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPublisherId(String.valueOf(ad.getAdvAccId()), osType.getNumber());
                                }

                                if(StringUtils.isBlank(materialMeta.getAppName()) || StringUtils.isBlank(materialMeta.getPackageName())
                                    || StringUtils.isBlank(materialMeta.getPublisherName()) || StringUtils.isBlank(materialMeta.getPrivacyLink())
                                    || StringUtils.isBlank(materialMeta.getPermissionLink()) || StringUtils.isBlank(materialMeta.getFeatureLink())){
                                    if(null == appInfoBaseModel){
                                        runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.APP_BASE_INFO_NULL_VALUE);
                                        PerfUtils.perf("invalidAd", Tag.of("reason", "app base info is deficiency"));
                                        return false;
                                    }
                                }
                            }
                            break;
                        case OPEN_IN_APP:
                            if (StringUtils.isBlank(materialMeta.getTargetUrl())) {
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by interactionType Open in app"));
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.INTERACTION_TYPE_OPEN_APP_VALUE);
                                log.info("OPEN_IN_APP drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                            break;
                        case WECHAT_MINI_PROGRAM:
                            if (StringUtils.isBlank(materialMeta.getWechatMiniProgramOriginId())) {
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by interactionType wechat origin id"));
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.INTERACTION_TYPE_WECHAT_ORIGIN_VALUE);
                                log.info("WECHAT_MINI_PROGRAM drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                            break;
                    }
                    //check by adType
                    AdType adType = runTimeContext.getAdType();
                    switch (adType) {
                        case FEED:
                            if (StringUtils.isBlank(materialMeta.getTitle())) {
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.ADTYPE_FEED_TITLE_ERROR_VALUE);
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by adType feed title"));
                                log.info("FEED drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                    }
                    //check by sourceType
                    switch (sourceType) {
                        case PERF:
                            if (StringUtils.isBlank(ad.getAdId())
                                    || ad.getAdvAccId() == 0
                                    || ad.getCampId() == 0
                                    || ad.getGroupId() == 0) {
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.PERF_ID_EMPTY_VALUE);
                                PerfUtils.perf("invalidAd", Tag.of("reason", "error by adpool, perf id empty"));
                                log.info("PERF drop requestId={} bidResponse={} placementId={}",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                    }

                    // 送审校验
                    if (RequestType.CUSTOM_API.equals(runTimeContext.getRequestType())) {
                        //RateLogger.rateLogger(log).info("creative audit begin");
                        if (RuntimeConstant.PLACEMENT_AUDIT_STYLE_BEFORE_PUTTING.equals(runTimeContext.getPlacementModel().getAuditStyle())) {
                            String pubAccountId = runTimeContext.getPlacementModel().getAccountId();
                            String channelName;
                            if (sourceType.equals(SourceType.SATURN)) {
                                channelName = finalBidResponse.getSaturnDsp();
                            } else {
                                channelName = Long.toString(ad.getAdvAccId());
                            }
                            String creativeAuditKey = pubAccountId + "_" + channelName;
                            String auditCreativeId = getCreativeAuditId(creativeAuditKey,ad.getAdId());
                            if (StringUtils.isBlank(auditCreativeId)) {
                                runTimeContext.getDspMap().put(sourceTypeStr, DspStaus.CREATIVE_AUDIT_NOT_PASS_VALUE);
                                PerfUtils.perf("invalidAd", Tag.of("reason", "creative audit not pass"));
                                log.info("PreAudit drop requestId={} bidResponse={} placementId={} creativeAuditKey={} adId={} ",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId(),
                                        creativeAuditKey, ad.getAdId());
                                return false;
                            }
                            runTimeContext.setCreativeAuditId(auditCreativeId);
                        }
                    }
                    // tracker size check
                    if (materialMeta.getImpUrlCount() != 0 && materialMeta.getClickUrlCount() != 0){
                        PerfUtils.perf("dsp.imp.count",Tag.of("size",String.valueOf(materialMeta.getImpUrlCount())), Tag.of("sourceType",String.valueOf(bidResponse.getSourceType())));
                        PerfUtils.perf("dsp.click.count",Tag.of("size",String.valueOf(materialMeta.getClickUrlCount())), Tag.of("sourceType",String.valueOf(bidResponse.getSourceType())));
                        //微博代理流量
                        if (runTimeContext.getBidRequest().getPubAccountId().equals("0fa7f8c2b6a56de520e844829a7885db")){
                            if (materialMeta.getImpUrlCount() >= 2 || materialMeta.getClickUrlCount() >= 2){
                                PerfUtils.perf("invalidAd", Tag.of("reason", "tracker size limit"));
                                log.info("TrackerSize drop requestId={} bidResponse={} placementId={} ",
                                        runTimeContext.getRequestId(), finalBidResponse, runTimeContext.getPlacementId());
                                return false;
                            }
                        }
                    }

                    return true;
                }).collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(validAdList) ? BidDropReason.VALID : BidDropReason.AD_INVALID;
    }

    private String getCreativeAuditId(String creativeAuditKey, String adId) {
        String auditCreativeId = null;
        Map<String, Map<String, String>> ossCreativeAuditMap = creativeAuditRepo.getCreativeAuditMap();

        Map<String, String> ossCreativeMap = ossCreativeAuditMap.get(creativeAuditKey);
        if (MapUtils.isNotEmpty(ossCreativeMap)) {
            auditCreativeId = ossCreativeMap.get(adId);
        }
        return auditCreativeId;
    }


    public List<BidResponse> repeatCreative(RunTimeContext runTimeContext, List<BidResponse> validAdList) {
        //开发者 送审素材处理
        String pubAccountId = runTimeContext.getPlacementModel().getAccountId();
        List<BidResponse> bidResponsesNew = new ArrayList<>();
        // 指定开发者替换素材 (只支持开屏)
        if (CollectionUtils.isNotEmpty(validAdList)
                && CREATIVE_REPEAT_PUB_LIST.get().contains((pubAccountId))
                && AdType.SPLASH.getNumber() == runTimeContext.getAdType().getNumber()) {
            validAdList.forEach(bidResponse -> {
                //开发者素材替换
                List<Ad> adsList = bidResponse.getAdsList();
                adsList = adsList.stream().map(ad -> {
                    Map<?, ?> map = CREATIVE_REPEAT_MAPPING.get();
                    List<String> listAds = (List<String>) map.get(pubAccountId);
                    if (CollectionUtils.isNotEmpty(listAds) && listAds.contains(ad.getSaturnChannelId())) {
                        LogUtils.logInfo(runTimeContext.getRequestId(), "creativeSaturnChannelId {},pubAccountId {}",ad.getSaturnChannelId(),pubAccountId);
                        Map<String, List<MaterialInfo>> mapListMaterialInfo = materialInfoRepo.getMapListMaterialInfo();
                        if (MapUtils.isNotEmpty(mapListMaterialInfo)) {
                            MaterialMeta creative = ad.getCreative();
                            if (creative.getCreativeType() == CreativeType.IMAGE_VALUE
                                    || creative.getCreativeType() == CreativeType.GIF_VALUE) {
                                //只替换图片类型 （原始素材的宽高）
                                int width = creative.getImage().getWidth();
                                int height = creative.getImage().getHeight();
                                String creativeKey = SunStringUtils.fracReduction(width, height).replace("/", "_");
                                String adCreativeKey = pubAccountId.concat("_").concat(ad.getSaturnChannelId()).concat("_").concat(creativeKey);
                                LogUtils.logInfo(runTimeContext.getRequestId(), "adCreativeKey {},width {},height {}", adCreativeKey, width, height);
                                List<MaterialInfo> materialInfos = mapListMaterialInfo.get(adCreativeKey);
                                if (CollectionUtils.isEmpty(materialInfos)) {
                                    LogUtils.logInfo(runTimeContext.getRequestId(), "creative repeat creativeIsNull,adCreativeKey {}", adCreativeKey);
                                    //默认取9_16比例尺寸的
                                    materialInfos = mapListMaterialInfo.get(pubAccountId.concat("_").concat(ad.getSaturnChannelId()).concat("_9_16"));
                                }
                                LogUtils.logInfo(runTimeContext.getRequestId(), "creativeRepeat beforeAdId {},beforeImageUrl {}",
                                        ad.getAdId(), creative.getImage().getUrl());
                                if(CollectionUtils.isNotEmpty(materialInfos)){
                                    MaterialInfo materialInfo = materialInfos.get(new Random().nextInt(materialInfos.size()));
                                    //adid  和 image 替换
                                    Image image = creative.getImage().toBuilder().setUrl(materialInfo.getImgUrl()).build();
                                    MaterialMeta materialMetaNew = creative.toBuilder().setImage(image).build();
                                    ad = ad.toBuilder().setAdId(materialInfo.getCreativeId()).setCreative(materialMetaNew).build();
                                    PerfUtils.perf("sun.creativeRepeat", Tag.of("status", "success"), Tag.of("channel", ad.getSaturnChannelId()),Tag.of("publisher", pubAccountId));
                                    LogUtils.logInfo(runTimeContext.getRequestId(), "creativeRepeat afterAdId {},afterImageUrl {}",
                                            materialInfo.getCreativeId(), materialInfo.getImgUrl());
                                }
                            }
                        }
                    }
                    return ad;
                }).collect(Collectors.toList());
                bidResponsesNew.add(bidResponse.toBuilder().clearAds().addAllAds(adsList).build());
            });
            LogUtils.logInfo(runTimeContext.getRequestId(),"creativeAfterResult {}",JSON.toJSONString(bidResponsesNew));
            return bidResponsesNew;
        }
        return validAdList;
    }
}