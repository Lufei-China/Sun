package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.api.InteractionType;
import cn.inmobi.pb.sun.rr.base.User;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.component.model.rr.ApiV33Response.Ads;
import com.inmobi.supply.component.model.rr.ApiV33Response.Ads.PubContent;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.FIX_BID;

/**
 * Api3.1 && Api3.3 envelop
 *
 * @author andrew.Ji
 */
@Service
@Slf4j
public class ApiV33EnvelopWorker implements IEnvelop {


    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        try {
            FormatterModel formatterModel = runTimeContext.getFormatterModel();
            RequestType requestType = runTimeContext.getRequestType();
            String formatAd = "";
            byte[] formatAdBytes = null;
            try {

                PubContent pubContent33 = new PubContent();
                pubContent33.setDescription(formatterModel.getDescription());
                pubContent33.setTitle(formatterModel.getTitle());
                pubContent33.setLandingURL(formatterModel.getLandingUrl());
                pubContent33.setCta(formatterModel.getCtaText());
                PubContent.Image image = new PubContent.Image();
                image.setWidth((float) RuntimeConstant.DEFAULT_ICON_WIDTH);
                image.setHeight((float) RuntimeConstant.DEFAULT_ICON_HEIGHT);
                image.setUrl(formatterModel.getIconUrl());
                image.setAspectRatio(RuntimeConstant.DEFAULT_ICON_ASPECT_RATIO);
                pubContent33.setIcon(image);

                if (formatterModel.getCreativeType() == CreativeType.IMAGE
                        || formatterModel.getCreativeType() == CreativeType.GIF) {
                    PubContent.Image screenshotsImage = new PubContent.Image();
                    screenshotsImage.setWidth((float) formatterModel.getImage().getWidth());
                    screenshotsImage.setHeight((float) formatterModel.getImage().getHeight());
                    screenshotsImage.setUrl(formatterModel.getImage().getUrl());
                    screenshotsImage.setAspectRatio(formatterModel.getImage().getAspectRatio().doubleValue());
                    pubContent33.setScreenshots(screenshotsImage);
                }
                if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    PubContent.RespVideo respVideo = new PubContent.RespVideo();
                    respVideo.setUrl(formatterModel.getVideo().getVideoUrl());
                    respVideo.setSize((float) formatterModel.getVideo().getSize());
                    respVideo.setResolution(formatterModel.getVideo().getResolution());
                    respVideo.setVideoDuration((float) formatterModel.getVideo().getVideoDuration());
                    if (StringUtils.isNotBlank(formatterModel.getVideo().getCoverUrl())) {
                        PubContent.Image screenshotsImage = new PubContent.Image();
                        screenshotsImage.setWidth((float) formatterModel.getVideo().getCoverWidth());
                        screenshotsImage.setHeight((float) formatterModel.getVideo().getCoverHeight());
                        screenshotsImage.setUrl(formatterModel.getVideo().getCoverUrl());
                        pubContent33.setScreenshots(screenshotsImage);
                    }
                    pubContent33.setVideos(respVideo);
                }

                Ads ads33 = new Ads();
                ads33.setAdid(runTimeContext.getWinBidResponse().getAds(0).getAdId());
                ads33.setBidPassthrough(false);
                ads33.setBidSharing(0);
                User user = runTimeContext.getBaseRequest().getUser();
                if(CollectionUtils.isNotEmpty(user.getTagIdList())){
                    ads33.setTagId(user.getTagIdList());
                }
//                        ads33.setBid(0L);
                //ads33.setBid(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
                if (FIX_BID.get().contains(runTimeContext.getPlacementId())) {
                    ads33.setBid(runTimeContext.getPlacementModel().getPubExpectation());
                }
//                        if (DYNAMIC_BID.get().contains(runTimeContext.getPlacementId())) {
//                            ads33.setBid(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
//                        }
                if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getBidPassthrough() == 1) {
                    ads33.setBid(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
                }
                if (requestType == RequestType.API_3_4_1) {
                    InteractionType interactionType = InteractionType.forNumber(runTimeContext.getWinAd().getCreative().getInteractionType());
                    switch (interactionType) {
                        case OPEN_IN_APP:
                            ads33.setInteractionType(1);
                            break;
                        case DOWNLOAD:
                            ads33.setInteractionType(2);
                            PubContent.AppInfo appInfo = new PubContent.AppInfo();
                            appInfo.setAppName(formatterModel.getAppName());
                            appInfo.setPackageName(formatterModel.getPackageName());
                            appInfo.setPublisherName(formatterModel.getPublisherName());
                            appInfo.setVersion(formatterModel.getVersion());
                            appInfo.setPrivacyLink(formatterModel.getPrivacyLink());
                            appInfo.setPermissionLink(formatterModel.getPermissionLink());
                            pubContent33.setAppInfo(appInfo);
                            break;
                        case DEEPLINK:
                            ads33.setInteractionType(3);
                            break;
                        case WECHAT_MINI_PROGRAM:
                            ads33.setInteractionType(4);
                            pubContent33.setOriginId(formatterModel.getOriginId());
                            break;
                    }

                    CreativeType creativeType = CreativeType.forNumber(runTimeContext.getWinAd().getCreative().getCreativeType());
                    switch (creativeType) {
                        case IMAGE:
                            ads33.setCreativeType(1);
                            break;
                        case VIDEO:
                            ads33.setCreativeType(2);
                            break;
                        case GIF:
                            ads33.setCreativeType(3);
                            break;
                    }

                } else {
                    ads33.setOpenExternal("DEEPLINK".equalsIgnoreCase(formatterModel.getOpenMode()));
                    ads33.setIsApp(formatterModel.isApp());
                }
                ads33.setExpirationTime(9000);
                ads33.setTargetUrl(formatterModel.getTargetUrl());
                ads33.setPubContent(pubContent33);
                if (StringUtils.isNotBlank(runTimeContext.getNewPeDealId())) {
                    ads33.setDealId(runTimeContext.getNewPeDealId());
                }

                Ads.EventTracking v33EventTracking = new Ads.EventTracking();
                Map<String, List<String>> eventTrackerNameMap = formatterModel.getEventTrackerNameMap();
                if (eventTrackerNameMap.containsKey("impressionTrackers")) {
                    v33EventTracking.setImpressionTrackers(eventTrackerNameMap.get("impressionTrackers"));
                }
                if (Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1){
                    if (eventTrackerNameMap.containsKey("winNoticeTrackers")) {
                        v33EventTracking.setWinNoticeTrackers(eventTrackerNameMap.get("winNoticeTrackers"));
                    }
                }
                if (eventTrackerNameMap.containsKey("clickTrackers")) {
                    v33EventTracking.setClickTrackers(eventTrackerNameMap.get("clickTrackers"));
                }
                if (eventTrackerNameMap.containsKey("videoStart")) {
                    v33EventTracking.setVideoStart(eventTrackerNameMap.get("videoStart"));
                }
                if (eventTrackerNameMap.containsKey("firstQuartile")) {
                    v33EventTracking.setFirstQuartile(eventTrackerNameMap.get("firstQuartile"));
                }
                if (eventTrackerNameMap.containsKey("midpoint")) {
                    v33EventTracking.setMidpoint(eventTrackerNameMap.get("midpoint"));
                }
                if (eventTrackerNameMap.containsKey("thirdQuartile")) {
                    v33EventTracking.setThirdQuartile(eventTrackerNameMap.get("thirdQuartile"));
                }
                if (eventTrackerNameMap.containsKey("videoComplete")) {
                    v33EventTracking.setVideoComplete(eventTrackerNameMap.get("videoComplete"));
                }
                if (eventTrackerNameMap.containsKey("videoAutoPlay")) {
                    v33EventTracking.setVideoAutoPlay(eventTrackerNameMap.get("videoAutoPlay"));
                }
                if (eventTrackerNameMap.containsKey("videoPause")) {
                    v33EventTracking.setVideoPause(eventTrackerNameMap.get("videoPause"));
                }
                if (eventTrackerNameMap.containsKey("videoResume")) {
                    v33EventTracking.setVideoResume(eventTrackerNameMap.get("videoResume"));
                }
                if (eventTrackerNameMap.containsKey("appInstalled")) {
                    v33EventTracking.setAppInstalled(eventTrackerNameMap.get("appInstalled"));
                }
                if (eventTrackerNameMap.containsKey("dplAttempt")) {
                    v33EventTracking.setDplAttempt(eventTrackerNameMap.get("dplAttempt"));
                }
                if (eventTrackerNameMap.containsKey("dplSuccess")) {
                    v33EventTracking.setDplSuccess(eventTrackerNameMap.get("dplSuccess"));
                }
                if (eventTrackerNameMap.containsKey("fallbackTrackers")) {
                    v33EventTracking.setFallbackTrackers(eventTrackerNameMap.get("fallbackTrackers"));
                }
                if (eventTrackerNameMap.containsKey("downX")) {
                    v33EventTracking.setDownX(eventTrackerNameMap.get("downX"));
                }
                if (eventTrackerNameMap.containsKey("downY")) {
                    v33EventTracking.setDownY(eventTrackerNameMap.get("downY"));
                }
                if (eventTrackerNameMap.containsKey("upX")) {
                    v33EventTracking.setUpX(eventTrackerNameMap.get("upX"));
                }
                if (eventTrackerNameMap.containsKey("upY")) {
                    v33EventTracking.setUpY(eventTrackerNameMap.get("upY"));
                }
                if (eventTrackerNameMap.containsKey("downloadStart")) {
                    v33EventTracking.setDownloadStart(eventTrackerNameMap.get("downloadStart"));
                }
                if (eventTrackerNameMap.containsKey("downloadFinish")) {
                    v33EventTracking.setDownloadFinish(eventTrackerNameMap.get("downloadFinish"));
                }
                if (eventTrackerNameMap.containsKey("installFinish")) {
                    v33EventTracking.setInstallFinish(eventTrackerNameMap.get("installFinish"));
                }
                if (!requestType.isSdk() && requestType.isAbove33()) {
                    if (eventTrackerNameMap.containsKey("videoAll")) {
                        v33EventTracking.setVideoTrackers(eventTrackerNameMap.get("videoAll"));
                    }
                }

                Ads.AdExt adExt = new Ads.AdExt();
                adExt.setSkadn(formatterModel.getSkad());
                ads33.setExt(adExt);
                ads33.setEventTracking(v33EventTracking);
                com.inmobi.supply.component.model.rr.ApiV33Response apiV33Response = new com.inmobi.supply.component.model.rr.ApiV33Response();
                List<Ads> ads33List = new ArrayList<>();
                ads33List.add(ads33);
                apiV33Response.setAds(ads33List);
                apiV33Response.setRequestId(runTimeContext.getRequestId());
                formatAd = ObjectMapperUtils.toJSON(apiV33Response);
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
        } catch (
                SunException e) {
            return Mono.error(e);
        } catch (
                Throwable e) {
            log.error("ApiEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}