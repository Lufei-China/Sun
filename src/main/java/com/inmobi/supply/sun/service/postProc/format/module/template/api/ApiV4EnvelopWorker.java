package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.api.InteractionType;
import cn.inmobi.pb.api.RevenueShareType;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.stars.bid.pb.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.CLICK_EXPIRATION_TIMESTAMP;
import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.IMPR_EXPIRATION_TIMESTAMP;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.FIX_BID;
import static com.inmobi.supply.sun.constant.TrackerEventInfo.*;

/**
 * Api3.1 && Api3.3 envelop
 *
 * @author andrew.Ji
 */
@Service
@Slf4j
public class ApiV4EnvelopWorker implements IEnvelop {

    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        try {
            FormatterModel formatterModel = runTimeContext.getFormatterModel();
            String formatAd;
            byte[] formatAdBytes;
            try {

                BidResponse.Builder responseV4Builder = BidResponse.newBuilder();
                responseV4Builder.setRequestId(runTimeContext.getRequestId());
                // imp
                BidResponse.Imp.Builder impBuilder = responseV4Builder.getImpBuilder();
                if (FIX_BID.get().contains(runTimeContext.getPlacementId())) {
                    impBuilder.setPrice(runTimeContext.getPlacementModel().getPubExpectation());
                }
                if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getBidPassthrough() == 1) {
                    impBuilder.setPrice(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
                }
                if(null != runTimeContext.getPlacementModel().getRevShareType()){
                    if(RevenueShareType.PD_REVENUE_SHARE.equals(RevenueShareType.forNumber(runTimeContext.getPlacementModel().getRevShareType()))){
                        impBuilder.setDealId(Optional.ofNullable(runTimeContext.getPlacementModel().getDealId()).orElse(""));
                        impBuilder.setDealPrice(Math.toIntExact(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY)));
                    }
                }
                // ad
                com.stars.bid.pb.Ad.Builder adBuilder = responseV4Builder.getAdBuilder();
                adBuilder.setAdid(runTimeContext.getWinBidResponse().getAds(0).getAdId());
                cn.inmobi.pb.sun.rr.base.User user = runTimeContext.getBaseRequest().getUser();
                if(CollectionUtils.isNotEmpty(user.getTagIdList())){
                    adBuilder.addAllTagId(user.getTagIdList());
                }

                if (formatterModel.getCreativeType() == CreativeType.IMAGE) {
                    adBuilder.setCreativeType(com.stars.bid.pb.CreativeType.IMAGE);
                } else if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    adBuilder.setCreativeType(com.stars.bid.pb.CreativeType.VIDEO);
                } else if (formatterModel.getCreativeType() == CreativeType.GIF) {
                    adBuilder.setCreativeType(com.stars.bid.pb.CreativeType.GIF);
                } else {
                    adBuilder.setCreativeType(com.stars.bid.pb.CreativeType.UNRECOGNIZED);
                }
                adBuilder.setRender(runTimeContext.getPlacementModel().getRenderType());
                Interaction.Builder interactionBuilder = adBuilder.getInteractionBuilder();
                InteractionType interactionType = InteractionType.forNumber(runTimeContext.getWinAd().getCreative().getInteractionType());
                switch (interactionType) {
                    case OPEN_IN_APP:
                        interactionBuilder.setType(com.stars.bid.pb.InteractionType.H5_LINK);
                        break;
                    case DOWNLOAD:
                        interactionBuilder.setType(com.stars.bid.pb.InteractionType.DOWNLOAD);
                        App.Builder appBuilder = App.newBuilder();
                        appBuilder.setAppName(Optional.ofNullable(formatterModel.getAppName()).orElse(""));
                        appBuilder.setBundleId(Optional.ofNullable(formatterModel.getPackageName()).orElse(""));
                        appBuilder.setPublisherName(Optional.ofNullable(formatterModel.getPublisherName()).orElse(""));
                        appBuilder.setVersion(Optional.ofNullable(formatterModel.getVersion()).orElse(""));
                        appBuilder.setPrivacyLink(Optional.ofNullable(formatterModel.getPrivacyLink()).orElse(""));
                        appBuilder.setPermissionLink(Optional.ofNullable(formatterModel.getPermissionLink()).orElse(""));
                        appBuilder.setPackSize(Optional.ofNullable(formatterModel.getPackSize()).orElse(0));
                        appBuilder.setAppFunctionDesc(Optional.ofNullable(formatterModel.getAppFunctionDesc()).orElse(""));
                        interactionBuilder.setApp(appBuilder.build());
                        break;
                    case DEEPLINK:
                        interactionBuilder.setType(com.stars.bid.pb.InteractionType.DEEPLINK);
                        interactionBuilder.setDeeplinkUrl(formatterModel.getLandingUrl());
                        App.Builder appBuilder1 = App.newBuilder();
                        appBuilder1.setAppName(formatterModel.getAppName());
                        appBuilder1.setBundleId(formatterModel.getPackageName());
                        interactionBuilder.setApp(appBuilder1.build());
                        break;
                    case WECHAT_MINI_PROGRAM:
                        interactionBuilder.setOriginId(formatterModel.getOriginId());
                        interactionBuilder.setDeeplinkUrl(formatterModel.getLandingUrl());
                        interactionBuilder.setType(com.stars.bid.pb.InteractionType.WECHAT_MINI_PROG);
                        break;
                    default:
                }
                interactionBuilder.setTargetUrl(formatterModel.getTargetUrl());
                Native.Builder nativeBuilder = adBuilder.getNativeBuilder();
                Text.Builder textBuilder = nativeBuilder.getTextBuilder();
                textBuilder.setTitle(formatterModel.getTitle());
                textBuilder.setDesc(formatterModel.getDescription());
                Icon.Builder iconBuilder = nativeBuilder.getIconBuilder();
                iconBuilder.setUrl(formatterModel.getIconUrl());
                iconBuilder.setHeight(RuntimeConstant.DEFAULT_ICON_HEIGHT);
                iconBuilder.setWidth(RuntimeConstant.DEFAULT_ICON_WIDTH);
                if (formatterModel.getCreativeType() == CreativeType.IMAGE || formatterModel.getCreativeType() == CreativeType.GIF) {
                    Image.Builder imageBuilder = nativeBuilder.getImageBuilder();
                    imageBuilder.setW(formatterModel.getImage().getWidth());
                    imageBuilder.setH(formatterModel.getImage().getHeight());
                    imageBuilder.setUrl(formatterModel.getImage().getUrl());
                }
                if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    Video.Builder videoBuilder = nativeBuilder.getVideoBuilder();
                    videoBuilder.setW(formatterModel.getVideo().getCoverWidth());
                    videoBuilder.setH(formatterModel.getVideo().getCoverHeight());
                    videoBuilder.setUrl(formatterModel.getVideo().getVideoUrl());
                    if(StringUtils.isNotBlank(formatterModel.getVideo().getCoverUrl())){
                        videoBuilder.setCoverUrl(formatterModel.getVideo().getCoverUrl());
                    }
                    videoBuilder.setDuration(Long.valueOf(formatterModel.getVideo().getVideoDuration()).intValue());
                }
                Map<String, List<String>> eventTrackerMap = formatterModel.getEventTrackerNameMap();
                if (eventTrackerMap.containsKey(IMPRESSION_TRACKER_URLS.getEventKey())) {
                    nativeBuilder.addAllImpressionTrackers(eventTrackerMap.get(IMPRESSION_TRACKER_URLS.getEventKey()));
                }
                if (Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1){
                    if (eventTrackerMap.containsKey(WIN_NOTICE_TRACKER_URLS.getEventKey())) {
                        nativeBuilder.setWinnoticeTracker(eventTrackerMap.get(WIN_NOTICE_TRACKER_URLS.getEventKey()).get(0));
                    }
                }
                if (eventTrackerMap.containsKey(CLICK_TRACKER_URLS.getEventKey())) {
                    nativeBuilder.addAllClickTrackers(eventTrackerMap.get(CLICK_TRACKER_URLS.getEventKey()));
                }
                List<Tracker> trackers = new ArrayList<>();
                //List<Tracker.Builder> conversionTrackersBuilderList = nativeBuilder.getConversionTrackersBuilderList();
                if (eventTrackerMap.containsKey(VIDEO_START.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_START);
                    builder.addAllTrackerUrls(eventTrackerMap.get(VIDEO_START.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(FIRST_QUARTILE.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_FIRST_QUARTILE);
                    builder.addAllTrackerUrls(eventTrackerMap.get(FIRST_QUARTILE.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(MIDPOINT.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_MID_POINT);
                    builder.addAllTrackerUrls(eventTrackerMap.get(MIDPOINT.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(THIRD_QUARTILE.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_THIRD_QUARTILE);
                    builder.addAllTrackerUrls(eventTrackerMap.get(THIRD_QUARTILE.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(VIDEO_COMPLETE.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_COMPLETE);
                    builder.addAllTrackerUrls(eventTrackerMap.get(VIDEO_COMPLETE.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(VIDEO_AUTO_PLAY.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_AUTO_PLAY);
                    builder.addAllTrackerUrls(eventTrackerMap.get(VIDEO_AUTO_PLAY.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(VIDEO_PAUSE.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_PAUSE);
                    builder.addAllTrackerUrls(eventTrackerMap.get(VIDEO_PAUSE.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(VIDEO_RESUME.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.VIDEO_RESUME);
                    builder.addAllTrackerUrls(eventTrackerMap.get(VIDEO_RESUME.getEventKey()));
                    trackers.add(builder.build());
                }

                if (eventTrackerMap.containsKey(DOWNLOAD_START_TRACKERS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DOWNLOAD_START);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DOWNLOAD_START_TRACKERS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(DOWNLOAD_FINISH_TRACKERS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DOWNLOAD_FINISH);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DOWNLOAD_FINISH_TRACKERS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(INSTALL_FINISH_TRACKERS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.INSTALL_FINISH);
                    builder.addAllTrackerUrls(eventTrackerMap.get(INSTALL_FINISH_TRACKERS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(APP_INSTALLED_TRACKERS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.APP_INSTALLED);
                    builder.addAllTrackerUrls(eventTrackerMap.get(APP_INSTALLED_TRACKERS.getEventKey()));
                    trackers.add(builder.build());
                }

                if (eventTrackerMap.containsKey(DEEPLINK_ATTEMPT_TRACKER_URLS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DEEPLINK_ATTEMPT);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DEEPLINK_ATTEMPT_TRACKER_URLS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(DEEPLINK_SUCCESS_TRACKER_URLS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DEEPLINK_SUCCESS);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DEEPLINK_SUCCESS_TRACKER_URLS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(FALLBACK_TRACKERS.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DEEPLINK_FALLBACK);
                    builder.addAllTrackerUrls(eventTrackerMap.get(FALLBACK_TRACKERS.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(DOWN_X.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DOWN_X);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DOWN_X.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(DOWN_Y.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.DOWN_Y);
                    builder.addAllTrackerUrls(eventTrackerMap.get(DOWN_Y.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(UP_X.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.UP_X);
                    builder.addAllTrackerUrls(eventTrackerMap.get(UP_X.getEventKey()));
                    trackers.add(builder.build());
                }
                if (eventTrackerMap.containsKey(UP_Y.getEventKey())) {
                    Tracker.Builder builder = Tracker.newBuilder();
                    builder.setEventType(Tracker.TrackerType.UP_Y);
                    builder.addAllTrackerUrls(eventTrackerMap.get(UP_Y.getEventKey()));
                    trackers.add(builder.build());
                }
                nativeBuilder.addAllConversionTrackers(trackers);
                long nowLong = System.currentTimeMillis();
                responseV4Builder.setProcessTimeMs((int) (nowLong - runTimeContext.getStartTime()));
                responseV4Builder.setImprExpirationTimestamp(nowLong + IMPR_EXPIRATION_TIMESTAMP.get()*1000);
                responseV4Builder.setClickExpirationTimestamp(nowLong + CLICK_EXPIRATION_TIMESTAMP.get()*1000);
                BidResponse responseV4 = responseV4Builder.build();
                formatAdBytes = responseV4.toByteArray();
                formatAd = responseV4.toString();
            } catch (Exception e) {
                log.error("ApiEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
                throw new SunException(TermCode.SERVER_ERROR, runTimeContext, e);
            }
            BaseResponse.Builder builder = BaseResponse.newBuilder()
                    .setOutputString(formatAd)
                    .setOutputBytes(formatAdBytes == null ? formatAd.getBytes() : formatAdBytes)
                    .setCharset(Charsets.UTF_8)
                    .setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
            builder.setHeader("im-id", runTimeContext.getRequestId());
            BaseResponse baseResponse = builder.build();
            runTimeContext.setBaseResponse(baseResponse);
            return Mono.just(baseResponse);
        } catch (SunException e) {
            return Mono.error(e);
        } catch (Throwable e) {
            log.error("ApiEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}