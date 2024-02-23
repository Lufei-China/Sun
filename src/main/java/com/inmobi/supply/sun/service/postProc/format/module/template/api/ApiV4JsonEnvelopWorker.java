package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.api.InteractionType;
import cn.inmobi.pb.api.RevenueShareType;
import cn.inmobi.pb.sun.rr.base.User;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.component.model.rr.ApiV40Response;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.utils.LogUtils;
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
import static com.inmobi.supply.sun.constant.TrackerEventInfo.CLICK_TRACKER_URLS;


@Service
@Slf4j
public class ApiV4JsonEnvelopWorker implements IEnvelop {

    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        try {
            FormatterModel formatterModel = runTimeContext.getFormatterModel();
            ApiV40Response apiV40Response = new ApiV40Response();
            apiV40Response.setRequestId(runTimeContext.getRequestId());
            String formatAd;
            try {
                // imp
                ApiV40Response.Imp imp = new ApiV40Response.Imp();
                if (FIX_BID.get().contains(runTimeContext.getPlacementId())) {
                    imp.setPrice(runTimeContext.getPlacementModel().getPubExpectation());
                }
                if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getBidPassthrough() == 1) {
                    imp.setPrice(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
                }
                if (null != runTimeContext.getPlacementModel().getRevShareType()) {
                    if (RevenueShareType.PD_REVENUE_SHARE.equals(RevenueShareType.forNumber(runTimeContext.getPlacementModel().getRevShareType()))) {
                        imp.setDealId(Optional.ofNullable(runTimeContext.getPlacementModel().getDealId()).orElse(""));
                        imp.setDealPrice(Math.toIntExact(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY)));
                    }
                }
                apiV40Response.setImp(imp);

                // ad
                ApiV40Response.Ad ad = new ApiV40Response.Ad();
                ad.setAdid(runTimeContext.getWinBidResponse().getAds(0).getAdId());
                if (formatterModel.getCreativeType() == CreativeType.IMAGE) {
                    ad.setCreativeType(ApiV40Response.CreativeType.IMAGE);
                } else if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    ad.setCreativeType(ApiV40Response.CreativeType.VIDEO);
                } else if (formatterModel.getCreativeType() == CreativeType.GIF) {
                    ad.setCreativeType(ApiV40Response.CreativeType.GIF);
                } else {
                    ad.setCreativeType(ApiV40Response.CreativeType.UNRECOGNIZED);
                }
                ad.setRender(runTimeContext.getPlacementModel().getRenderType());

                // Interaction
                ApiV40Response.Interaction interaction = new ApiV40Response.Interaction();
                InteractionType interactionType = InteractionType.forNumber(runTimeContext.getWinAd().getCreative().getInteractionType());

                switch (interactionType) {
                    case OPEN_IN_APP:
                        interaction.setType(ApiV40Response.InteractionType.H5_LINK);
                        break;
                    case DOWNLOAD:
                        interaction.setType(ApiV40Response.InteractionType.DOWNLOAD);
                        ApiV40Response.App app = new ApiV40Response.App();
                        app.setAppName(Optional.ofNullable(formatterModel.getAppName()).orElse(""));
                        app.setBundleId(Optional.ofNullable(formatterModel.getPackageName()).orElse(""));
                        app.setPublisherName(Optional.ofNullable(formatterModel.getPublisherName()).orElse(""));
                        app.setVersion(Optional.ofNullable(formatterModel.getVersion()).orElse(""));
                        app.setPrivacyLink(Optional.ofNullable(formatterModel.getPrivacyLink()).orElse(""));
                        app.setPermissionLink(Optional.ofNullable(formatterModel.getPermissionLink()).orElse(""));
                        app.setPackSize(Optional.ofNullable(formatterModel.getPackSize()).orElse(0));
                        app.setAppFunctionDesc(Optional.ofNullable(formatterModel.getAppFunctionDesc()).orElse(""));
                        interaction.setApp(app);
                        break;
                    case DEEPLINK:
                        interaction.setType(ApiV40Response.InteractionType.DEEPLINK);
                        interaction.setDeeplinkUrl(formatterModel.getLandingUrl());
                        ApiV40Response.App appDeep = new ApiV40Response.App();
                        appDeep.setAppName(formatterModel.getAppName());
                        appDeep.setBundleId(formatterModel.getPackageName());
                        interaction.setApp(appDeep);
                        break;
                    case WECHAT_MINI_PROGRAM:
                        interaction.setOriginId(formatterModel.getOriginId());
                        interaction.setDeeplinkUrl(formatterModel.getLandingUrl());
                        interaction.setType(ApiV40Response.InteractionType.WECHAT_MINI_PROG);
                        break;
                    default:
                }
                interaction.setTargetUrl(formatterModel.getTargetUrl());
                ad.setInteraction(interaction);

                ApiV40Response.Native aNative = new ApiV40Response.Native();
                ApiV40Response.Text text = new ApiV40Response.Text();
                text.setTitle(formatterModel.getTitle());
                text.setDesc(formatterModel.getDescription());

                aNative.setText(text);

                ApiV40Response.Icon icon = new ApiV40Response.Icon();
                icon.setUrl(formatterModel.getIconUrl());
                icon.setHeight(RuntimeConstant.DEFAULT_ICON_HEIGHT);
                icon.setWidth(RuntimeConstant.DEFAULT_ICON_WIDTH);

                aNative.setIcon(icon);

                if (formatterModel.getCreativeType() == CreativeType.IMAGE || formatterModel.getCreativeType() == CreativeType.GIF) {
                    ApiV40Response.Image image = new ApiV40Response.Image();
                    image.setW(formatterModel.getImage().getWidth());
                    image.setH(formatterModel.getImage().getHeight());
                    image.setUrl(formatterModel.getImage().getUrl());
                    aNative.setImage(image);
                }
                if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    ApiV40Response.Video video = new ApiV40Response.Video();
                    video.setW(formatterModel.getVideo().getCoverWidth());
                    video.setH(formatterModel.getVideo().getCoverHeight());
                    video.setUrl(formatterModel.getVideo().getVideoUrl());
                    if (StringUtils.isNotBlank(formatterModel.getVideo().getCoverUrl())) {
                        video.setCoverUrl(formatterModel.getVideo().getCoverUrl());
                    }
                    video.setDuration(Optional.of(formatterModel.getVideo().getVideoDuration()).orElse(0l).intValue());
                    aNative.setVideo(video);
                }

                //Tracker
                Map<String, List<String>> eventTrackerMap = formatterModel.getEventTrackerNameMap();
                if (eventTrackerMap.containsKey(IMPRESSION_TRACKER_URLS.getEventKey())) {
                    aNative.setImpressionTrackers(eventTrackerMap.get(IMPRESSION_TRACKER_URLS.getEventKey()));
                }
                if (Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1) {
                    if (eventTrackerMap.containsKey(WIN_NOTICE_TRACKER_URLS.getEventKey())) {
                        aNative.setWinnoticeTracker(eventTrackerMap.get(WIN_NOTICE_TRACKER_URLS.getEventKey()).get(0));
                    }
                }
                if (eventTrackerMap.containsKey(CLICK_TRACKER_URLS.getEventKey())) {
                    aNative.setClickTrackers(eventTrackerMap.get(CLICK_TRACKER_URLS.getEventKey()));
                }

                List<ApiV40Response.Tracker> trackers = new ArrayList<>();
                if (eventTrackerMap.containsKey(VIDEO_START.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_START);
                    tracker.setTrackerUrls(eventTrackerMap.get(VIDEO_START.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(FIRST_QUARTILE.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_FIRST_QUARTILE);
                    tracker.setTrackerUrls(eventTrackerMap.get(FIRST_QUARTILE.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(MIDPOINT.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_MID_POINT);
                    tracker.setTrackerUrls(eventTrackerMap.get(MIDPOINT.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(THIRD_QUARTILE.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_THIRD_QUARTILE);
                    tracker.setTrackerUrls(eventTrackerMap.get(THIRD_QUARTILE.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(VIDEO_COMPLETE.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_COMPLETE);
                    tracker.setTrackerUrls(eventTrackerMap.get(VIDEO_COMPLETE.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(VIDEO_AUTO_PLAY.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_AUTO_PLAY);
                    tracker.setTrackerUrls(eventTrackerMap.get(VIDEO_AUTO_PLAY.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(VIDEO_PAUSE.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_PAUSE);
                    tracker.setTrackerUrls(eventTrackerMap.get(VIDEO_PAUSE.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(VIDEO_RESUME.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.VIDEO_RESUME);
                    tracker.setTrackerUrls(eventTrackerMap.get(VIDEO_RESUME.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DOWNLOAD_START_TRACKERS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DOWNLOAD_START);
                    tracker.setTrackerUrls(eventTrackerMap.get(DOWNLOAD_START_TRACKERS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DOWNLOAD_FINISH_TRACKERS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DOWNLOAD_FINISH);
                    tracker.setTrackerUrls(eventTrackerMap.get(DOWNLOAD_FINISH_TRACKERS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(INSTALL_FINISH_TRACKERS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.INSTALL_FINISH);
                    tracker.setTrackerUrls(eventTrackerMap.get(INSTALL_FINISH_TRACKERS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(APP_INSTALLED_TRACKERS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.APP_INSTALLED);
                    tracker.setTrackerUrls(eventTrackerMap.get(APP_INSTALLED_TRACKERS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DEEPLINK_ATTEMPT_TRACKER_URLS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DEEPLINK_ATTEMPT);
                    tracker.setTrackerUrls(eventTrackerMap.get(DEEPLINK_ATTEMPT_TRACKER_URLS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DEEPLINK_SUCCESS_TRACKER_URLS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DEEPLINK_SUCCESS);
                    tracker.setTrackerUrls(eventTrackerMap.get(DEEPLINK_SUCCESS_TRACKER_URLS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(FALLBACK_TRACKERS.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DEEPLINK_FALLBACK);
                    tracker.setTrackerUrls(eventTrackerMap.get(FALLBACK_TRACKERS.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DOWN_X.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DOWN_X);
                    tracker.setTrackerUrls(eventTrackerMap.get(DOWN_X.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(DOWN_Y.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.DOWN_Y);
                    tracker.setTrackerUrls(eventTrackerMap.get(DOWN_Y.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(UP_X.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.UP_X);
                    tracker.setTrackerUrls(eventTrackerMap.get(UP_X.getEventKey()));
                    trackers.add(tracker);
                }
                if (eventTrackerMap.containsKey(UP_Y.getEventKey())) {
                    ApiV40Response.Tracker tracker = new ApiV40Response.Tracker();
                    tracker.setEventType(ApiV40Response.TrackerType.UP_Y);
                    tracker.setTrackerUrls(eventTrackerMap.get(UP_Y.getEventKey()));
                    trackers.add(tracker);
                }
                aNative.setConversionTrackers(trackers);
                ad.setNativeInfo(aNative);
                User user = runTimeContext.getBaseRequest().getUser();
                if(CollectionUtils.isNotEmpty(user.getTagIdList())){
                    ad.setTagId(user.getTagIdList());
                }
                apiV40Response.setAd(ad);

                long nowLong = System.currentTimeMillis();
                apiV40Response.setProcessTimeMs((int) (nowLong - runTimeContext.getStartTime()));
                apiV40Response.setImprExpirationTimestamp(nowLong + IMPR_EXPIRATION_TIMESTAMP.get() * 1000);
                apiV40Response.setClickExpirationTimestamp(nowLong + CLICK_EXPIRATION_TIMESTAMP.get() * 1000);
                formatAd = ObjectMapperUtils.toJSON(apiV40Response);
                LogUtils.logInfo(runTimeContext.getRequestId(),"ApiV4JsonEnvelopWorker response {}", JSON.toJSONString(apiV40Response));
            } catch (Exception e) {
                log.error("ApiV4JsonEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
                throw new SunException(TermCode.SERVER_ERROR, runTimeContext, e);
            }
            BaseResponse.Builder builder = BaseResponse.newBuilder()
                    .setOutputString(formatAd)
                    .setOutputBytes(formatAd.getBytes())
                    .setCharset(Charsets.UTF_8)
                    .setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
            builder.setHeader("im-id", runTimeContext.getRequestId());
            BaseResponse baseResponse = builder.build();
            runTimeContext.setBaseResponse(baseResponse);
            return Mono.just(baseResponse);
        } catch (SunException e) {
            return Mono.error(e);
        } catch (Throwable e) {
            log.error("ApiV4JsonEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}
