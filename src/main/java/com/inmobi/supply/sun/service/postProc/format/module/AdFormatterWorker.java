package com.inmobi.supply.sun.service.postProc.format.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.inmobi.pb.api.*;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.*;
import com.inmobi.supply.component.model.adFormat.Image;
import com.inmobi.supply.component.model.adFormat.Video;
import com.inmobi.supply.component.model.redis.AppInfoBaseModel;
import com.inmobi.supply.component.model.rr.ApiV33Response;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.GoaRtt;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.AppInfoBaseRepo;
import com.inmobi.supply.sun.repo.redis.service.AppRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.preProc.call.MixFlowRateService;
import com.inmobi.supply.sun.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Mono;

import static cn.inmobi.pb.api.TrackingEvent.*;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.IOS_SDK_COMPATIBLE_PLACEMENTS;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.REPLACE_TITLE_PUB;
import static com.inmobi.supply.sun.utils.RandomUtils.dropByPercent;
import static com.inmobi.supply.sun.utils.WorkerUtils.httpsTrans;

/**
 * @author remi.li
 * <p>
 * prepare adFormat data
 */
@Slf4j
@Service
public class AdFormatterWorker implements ModuleWorker<Integer> {
    private static final String DEFAULT_ICON_URL = "https://i.l.inmobicdn.cn/banners/FileData/ea5cff559e4246c19dea4f6012c69c80.png";

    @Autowired
    private MixFlowRateService mixFlowRateService;

    @Autowired
    private AppInfoBaseRepo appInfoBaseRepo;

    @Value("${appInfoBase.permissionLink.prefix:https://cdn-supply.inmobi.cn/supply-ci/permissions/}")
    private String permissionLinkPrefix;
    @Value("${appInfoBase.appDesc.prefix:https://cdn-supply.inmobi.cn/supply-ci/appDesc/}")
    private String appDescPrefix;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "AdFormatterWorker");
        LogUtils.logInfo(runTimeContext.getRequestId(),"enter Envelope2Service AdFormatterWorker");
        try {
            Ad winAd = runTimeContext.getWinAd();
            OsType osType = runTimeContext.getDevice().getOsType();
            MaterialMeta creative = winAd.getCreative();
            FormatterModel.FormatterModelBuilder formatBuilder = FormatterModel.builder();
            String title = StringUtils.isNotBlank(creative.getTitle()) ? creative.getTitle() : "inmobi ad";
            String description;
            if (REPLACE_TITLE_PUB.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                if (StringUtils.isNotBlank(creative.getDescription())) {
                    description = creative.getDescription();
                } else if (StringUtils.isNotBlank(creative.getTitle())){
                    description = creative.getTitle();
                } else {
                    description = "inmobi ad";
                }
            } else {
                description = StringUtils.isNotBlank(creative.getDescription()) ? creative.getDescription() : "inmobi ad";
            }
            formatBuilder.requestId(runTimeContext.getRequestId());
            formatBuilder.title(title);
            formatBuilder.description(description);
            formatBuilder.creativeType(CreativeType.forNumber(creative.getCreativeType()));
            formatBuilder.appName(creative.getAppName());
            if (runTimeContext.getWinAd().getAdvAccId() == 435L) {
                formatBuilder.appName("捕鱼大作战");
            }

            formatBuilder.packageName(creative.getPackageName());
            String iconUrl = StringUtils.isNotBlank(creative.getIcon()) ? creative.getIcon() : DEFAULT_ICON_URL;
            formatBuilder.iconUrl(httpsTrans(runTimeContext, iconUrl));
            String parentViewWidth = runTimeContext.getSdkRequest().getOrDefault("a-parentViewWidth", "360");
            formatBuilder.parentViewWidth(Integer.parseInt(parentViewWidth));
            String deviceScreenSize = runTimeContext.getSdkRequest().getOrDefault("d-device-screen-size", "360x676");
            String[] size = deviceScreenSize.toLowerCase().split("x");
            if (size.length == 2) {
                formatBuilder.deviceScreenWidth(Integer.parseInt(size[0]));
                formatBuilder.deviceScreenHeight(Integer.parseInt(size[1]));
            }
            String targetUrl = httpsTrans(runTimeContext, creative.getTargetUrl());
            String downloadUrl = httpsTrans(runTimeContext, creative.getDownloadUrl());
            if (winAd.getSaturnChannelId().equals(SaturnDsp.GDT_CUSTOM_CHANNEL_ID)) {
                downloadUrl = targetUrl;
            }
            if (creative.getInteractionType() == InteractionType.DEEPLINK_VALUE) {
                // 调起应用商店时，返回五要素
                if (creative.getDeeplinkUrl().startsWith("market://details")) {
                    setDownloadInfo(runTimeContext, formatBuilder);
                }
                //deeplink ad
                formatBuilder.ctaText("了解更多");
                formatBuilder.isApp(false);
                //for api
                formatBuilder.targetUrl(targetUrl);
                formatBuilder.landingUrl(creative.getDeeplinkUrl());

                //for sdk
                formatBuilder.itemUrl(creative.getDeeplinkUrl());
                formatBuilder.fallbackUrl(targetUrl);

                formatBuilder.openMode(OpenModel.DEEPLINK.name());
                String nequalChannelTest = "154302836b21174192f4744167717056";
                if (IOS_SDK_COMPATIBLE_PLACEMENTS.get().contains(runTimeContext.getPlacementId())
                        && nequalChannelTest.equals(winAd.getSaturnChannelId())) {
                    formatBuilder.openMode(OpenModel.EXTERNAL.name());
                    formatBuilder.itemUrl(targetUrl);
                }
                formatBuilder.shouldAutoOpenLandingPage(true);
            }
            if (creative.getInteractionType() == InteractionType.OPEN_IN_APP_VALUE) {
                //** 为302混量检测，替换targetUrl
                PerfUtils.perf("sun.redirect302.count","ratio","all");
                boolean flag302 = dropByPercent(DynamicIntegerConfig.REDIRECT_302_RATE.get());
                boolean flagSend302 = false;
                if ((flag302 && runTimeContext.getPlacementModel() != null && 1 == runTimeContext.getPlacementModel().getMixFlowFlag())){
                    PerfUtils.perf("sun.redirect302.count","ratio","success");
                    LogUtils.logInfo(runTimeContext.getRequestId(),"redirect302.info.targetUrl {}",targetUrl);
                    targetUrl = mixFlowRateService.getTargetUrl(runTimeContext, targetUrl);
                    flagSend302 = true;
                }

                long groupId = winAd.getGroupId();
                if(DynamicListConfig.DEMAND_302_LIST.get().contains(groupId) && !flagSend302){
                    PerfUtils.perf("sun.redirect302.demand.count","status","success");
                    targetUrl = mixFlowRateService.getTargetUrl(runTimeContext, targetUrl);
                }

                //H5 ad
                formatBuilder.ctaText("了解更多");
                formatBuilder.isApp(false);

                //for api
                formatBuilder.targetUrl(targetUrl);
                if(!DynamicListConfig.ADAPTER_LIST.get().contains(runTimeContext.getPlacementId())) {
                    formatBuilder.landingUrl(targetUrl);
                }

                //for sdk
                formatBuilder.itemUrl(targetUrl);
                formatBuilder.fallbackUrl(targetUrl);
                formatBuilder.openMode(OpenModel.EXTERNAL.name());
                formatBuilder.shouldAutoOpenLandingPage(true);
            }

            if (creative.getInteractionType() == InteractionType.DOWNLOAD_VALUE) {
                setDownloadInfo(runTimeContext, formatBuilder);
                //download ad
                formatBuilder.ctaText("立即下载");
                formatBuilder.isApp(true);

                //for api
                formatBuilder.targetUrl(downloadUrl);
                if(!DynamicListConfig.ADAPTER_LIST.get().contains(runTimeContext.getPlacementId())) {
                    formatBuilder.landingUrl(downloadUrl);
                }

                //for sdk
                formatBuilder.itemUrl(downloadUrl);
                formatBuilder.fallbackUrl("");

                if (osType == OsType.IOS) {
                    Pattern pattern = Pattern.compile("id(\\d+)\\?");
                    Matcher matcher = pattern.matcher(downloadUrl);
                    if (matcher.find()) {
                        String appId = matcher.group(1);
                        formatBuilder.itemUrl(appId);
                    } else {
                        log.error("Extract appId from downloadUrl get error, " + runTimeContext.getRequestId());
                    }
                    formatBuilder.openMode(OpenModel.SKSTORE.name());
                    formatBuilder.fallbackUrl(downloadUrl);
                } else {
                    formatBuilder.openMode(OpenModel.DOWNLOAD.name());
                }
                formatBuilder.shouldAutoOpenLandingPage(true);
            }
            if (creative.getInteractionType() == InteractionType.WECHAT_MINI_PROGRAM_VALUE) {
                //H5 ad
                formatBuilder.ctaText("微信小程序");
                formatBuilder.isApp(false);
                formatBuilder.originId(creative.getWechatMiniProgramOriginId());

                //for api
                formatBuilder.targetUrl(targetUrl);
                formatBuilder.landingUrl(creative.getWechatMiniProgramPath());

                //for sdk
                formatBuilder.itemUrl(targetUrl);
                formatBuilder.fallbackUrl(targetUrl);
                formatBuilder.openMode(OpenModel.EXTERNAL.name());
                formatBuilder.shouldAutoOpenLandingPage(true);
            }

            formatBuilder.osType(runTimeContext.getDevice().getOsType());
            formatBuilder.sdkVersion(runTimeContext.getSdkVersion());

            if (creative.getCreativeType() == CreativeType.IMAGE_VALUE
                    || creative.getCreativeType() == CreativeType.GIF_VALUE) {
                //build image content
                BigDecimal bd = new BigDecimal(creative.getImage().getWidth()).divide(new BigDecimal(creative.getImage().getHeight()),2, RoundingMode.HALF_UP);
                BigDecimal aspectRatio = bd.setScale(2, RoundingMode.HALF_UP);
                Image image = new Image(creative.getImage().getWidth(), creative.getImage().getHeight(), httpsTrans(runTimeContext, creative.getImage().getUrl()), aspectRatio);
                formatBuilder.image(image);
            }
            if (creative.getCreativeType() == CreativeType.VIDEO_VALUE) {
                //build video content
                Video video = new Video();
                String resolution = creative.getVideo().getResolution();
                if(creative.getVideo().getCoverWidth() > 0 && creative.getVideo().getCoverHeight() > 0) {
                    video.setCoverWidth(creative.getVideo().getCoverWidth());
                    video.setCoverHeight(creative.getVideo().getCoverHeight());
                } else if (!StringUtils.isEmpty(resolution)) {
                    video.setCoverWidth(Integer.parseInt(resolution.split("\\*")[0].trim()));
                    video.setCoverHeight(Integer.parseInt(resolution.split("\\*")[1].trim()));
                }
                video.setCoverUrl(httpsTrans(runTimeContext,creative.getVideo().getCoverUrl()));
                video.setSize(creative.getVideo().getSize());
                video.setVideoDuration(creative.getVideo().getVideoDuration());
                video.setVideoUrl(httpsTrans(runTimeContext,creative.getVideo().getVideoUrl()));
                video.setResolution(creative.getVideo().getResolution());
                formatBuilder.video(video);
            }

            List<String> clientFillTrackerList = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(CLIENT_FILL, new ArrayList<>());

            List<String> renderTrackerList = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(VIEW, new ArrayList<>());

            List<String> clickTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(CLICK, new ArrayList<>());

            List<String> ctaTriggerFailTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(CTA_TRIGGER_FAIL, new ArrayList<>());

            List<String> vtaTriggerFailTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(VTA_TRIGGER_FAIL, new ArrayList<>());

            List<String> dplAttemptTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DEEPLINK_ATTEMPT, new ArrayList<>());

            List<String> dplSuccessTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DEEPLINK_SUCCESS, new ArrayList<>());

            List<String> downloaderInitTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DOWNLOAD_START, new ArrayList<>());

            List<String> downloadingTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DOWNLOADING, new ArrayList<>());

            List<String> downloadedTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DOWNLOAD_FINISH, new ArrayList<>());

            List<String> downloaderErrorTrackers = runTimeContext.getAdRunTimeInfo()
                    .getEventTrackers().getOrDefault(DOWNLOADER_ERROR, new ArrayList<>());
            List<Object> deckTrackers = new ArrayList<>();
            if (creative.getCreativeType() == CreativeType.VIDEO_VALUE) {
                List<String> startTrackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_START, new ArrayList<>());
                List<String> videoQ1Trackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_Q1, new ArrayList<>());
                List<String> videoQ2Trackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_Q2, new ArrayList<>());
                List<String> videoQ3Trackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_Q3, new ArrayList<>());
                List<String> videoCompleteTrackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_COMPLETE, new ArrayList<>());
                List<String> videoAutoPlayTrackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_AUTO_PLAY, new ArrayList<>());
                List<String> videoPauseTrackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_PAUSE, new ArrayList<>());
                List<String> videoResumeTrackers = runTimeContext.getAdRunTimeInfo()
                        .getEventTrackers().getOrDefault(VIDEO_RESUME, new ArrayList<>());
                if (CollectionUtils.isNotEmpty(renderTrackerList)) {
                    renderTrackerList.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker, "Impression")));
                }
                if (CollectionUtils.isNotEmpty(startTrackers)) {
                    startTrackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker, "start")));
                }
                if (CollectionUtils.isNotEmpty(videoQ1Trackers)) {
                    videoQ1Trackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "firstQuartile")));
                }
                if (CollectionUtils.isNotEmpty(videoQ2Trackers)) {
                    videoQ2Trackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "midpoint")));
                }
                if (CollectionUtils.isNotEmpty(videoQ3Trackers)) {
                    videoQ3Trackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "thirdQuartile")));
                }
                if (CollectionUtils.isNotEmpty(videoCompleteTrackers)) {
                    videoCompleteTrackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "complete")));
                }
                if (CollectionUtils.isNotEmpty(videoAutoPlayTrackers)) {
                    videoAutoPlayTrackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "autoplay")));
                }
                if (CollectionUtils.isNotEmpty(videoPauseTrackers)) {
                    videoPauseTrackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "pause")));
                }
                if (CollectionUtils.isNotEmpty(videoResumeTrackers)) {
                    videoResumeTrackers.forEach(tracker -> deckTrackers.add(new VideoTracker(tracker,  "resume")));
                }
            } else {
                if (CollectionUtils.isNotEmpty(clientFillTrackerList)) {
                    clientFillTrackerList.forEach(tracker -> deckTrackers.add(new Tracker(tracker, "URL_PING", "CLIENT_FILL")));
                }
                if (CollectionUtils.isNotEmpty(renderTrackerList)) {
                    renderTrackerList.forEach(tracker -> deckTrackers.add(new Tracker(tracker, "URL_PING", "RENDER")));
                }
            }
            if (CollectionUtils.isNotEmpty(ctaTriggerFailTrackers)) {
                ctaTriggerFailTrackers.forEach(tracker -> deckTrackers.add(new Tracker(tracker, "URL_PING", "CTA_FAIL")));
            }
            if (CollectionUtils.isNotEmpty(vtaTriggerFailTrackers)) {
                vtaTriggerFailTrackers.forEach(tracker -> deckTrackers.add(new Tracker(tracker, "URL_PING", "VTA_FAIL")));
            }
            formatBuilder.deckTrackers(ObjectMapperUtils.toJSON(deckTrackers));

            List<Tracker> ctaTrackers = new ArrayList<>();

            if (CollectionUtils.isNotEmpty(clickTrackers)) {
                clickTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "CLICK")));
            }


            if (creative.getInteractionType() == InteractionType.DEEPLINK_VALUE) {
                if (CollectionUtils.isNotEmpty(dplAttemptTrackers)) {
                    dplAttemptTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DEEPLINK_ATTEMPT")));
                }
                if (CollectionUtils.isNotEmpty(dplSuccessTrackers)) {
                    dplSuccessTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DEEPLINK_SUCCESS")));
                }
            }
            if (creative.getInteractionType() == InteractionType.DOWNLOAD_VALUE) {
                if (CollectionUtils.isNotEmpty(downloaderInitTrackers)) {
                    downloaderInitTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DOWNLOADER_INITIALIZED")));
                }
                if (CollectionUtils.isNotEmpty(downloadingTrackers)) {
                    downloadingTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DOWNLOADER_DOWNLOADING")));
                }
                if (CollectionUtils.isNotEmpty(downloadedTrackers)) {
                    downloadedTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DOWNLOADER_DOWNLOADED")));
                }
                if (CollectionUtils.isNotEmpty(downloaderErrorTrackers)) {
                    downloaderErrorTrackers.forEach(tracker -> ctaTrackers.add(new Tracker(tracker, "URL_PING", "DOWNLOADER_ERROR")));
                }
            }

            formatBuilder.ctaTrackers(ObjectMapperUtils.toJSON(ctaTrackers));
            Map<String, List<String>> stringListMap = convertEventTrackerMap(runTimeContext.getAdRunTimeInfo().getEventTrackers());
            formatBuilder.eventTrackerNameMap(stringListMap);
            formatBuilder.eventTrackers(runTimeContext.getAdRunTimeInfo().getEventTrackers());
            formatBuilder.sdkAllTrackers(ObjectMapperUtils.toJSON(stringListMap));
            //set encryptedCreativeId
            String adId = winAd.getAdId();
            if (StringUtils.isNotBlank(adId)) {
                try {
                    formatBuilder.encryptedAdId(WorkerUtils.getEncryptedCrId(adId));
                } catch (Exception e) {
                    formatBuilder.encryptedAdId("");
                    log.error("encrypted creativeId future get error", e);
                }
            }
            formatBuilder.bidPassthrough(false);
            if(runTimeContext.getPlacementModel().getIsBidSharing()!=null) {
                formatBuilder.bidSharing(runTimeContext.getPlacementModel().getIsBidSharing());
            }else {
                formatBuilder.bidSharing(0);
            }
            formatBuilder.creativeAuditId(runTimeContext.getCreativeAuditId());
            formatBuilder.encryptedBid("0");
            formatBuilder.hasBidEncrypt(true);
            String charge = "0";
            //set bid & encrypted bid
            if (runTimeContext.getPlacementModel().getBidPassthrough()!=null&&runTimeContext.getPlacementModel().getBidPassthrough() == 1) {
                charge = runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY).toString();
                formatBuilder.bidPassthrough(runTimeContext.getPlacementModel().getBidPassthrough()==1);
            }
            formatBuilder.bid(charge);
            formatBuilder.encryptedBid(charge);
            if (runTimeContext.getRequestType().isSdk()) {
                try {
                    String encryptedBid = getEncryptedBid(charge, runTimeContext, formatBuilder);
                    formatBuilder.encryptedBid(encryptedBid);
                } catch (Exception e) {
                    formatBuilder.hasBidEncrypt(false);
                    formatBuilder.encryptedBid("0");
                    log.error("encryptedBid future get error", e);
                }
            }
            if (StringUtils.isBlank(winAd.getSaturnChannelId())) {
                formatBuilder.advAccId(String.valueOf(winAd.getAdvAccId()));
            }else {
                formatBuilder.advAccId(String.valueOf(winAd.getSaturnChannelId()));
            }
            formatBuilder.adId(adId);
            if (CollectionUtils.isNotEmpty(runTimeContext.getBidSharingWinNotices())) {
                formatBuilder.winNotices(runTimeContext.getBidSharingWinNotices());
            }

            //skan
            ApiV33Response.Ads.AdExt.Skad skad = new ApiV33Response.Ads.AdExt.Skad();
            if (runTimeContext.getBidRequest().getAdslots(0).getSupportSkan()
                    && winAd.getIsSkan()) {
                if (runTimeContext.getWinBidResponse().getSourceType() == SourceType.SATURN_VALUE
                        && winAd.getDspSignSupport()) {
                    skad.setVersion(winAd.getSkadn().getVersion());
                    skad.setNetwork(winAd.getSkadn().getNetwork());
                    skad.setCampaign(winAd.getSkadn().getCampaign());
                    skad.setItunesitem(winAd.getSkadn().getItunesitem());
                    skad.setNonce(winAd.getSkadn().getNonce());
                    skad.setSourceapp(winAd.getSkadn().getSourceapp());
                    skad.setTimestamp(winAd.getSkadn().getTimestamp());
                    skad.setSignature(winAd.getSkadn().getSignature());
                    skad.setVtaSignature(winAd.getSkadn().getVtaSignature());
                } else {
                    skad.setNetwork(RuntimeConstant.INMOBI_NETWORK_ID);
                    skad.setCampaign(String.valueOf(winAd.getCampId()));
                    skad.setItunesitem(winAd.getCreative().getAdvAppId());
                    skad.setNonce(UUID.randomUUID().toString().toLowerCase());
                    skad.setSourceapp(String.valueOf(null != runTimeContext.getAppModel() ? runTimeContext.getAppModel().getItunesId() : null));
                    skad.setTimestamp(String.valueOf(System.currentTimeMillis()));
                    Version osVersion = runTimeContext.getBidRequest().getDevice().getOsVersion();
                    String skanVersion;
                    log.info("skad osVersion={} {} {}", osVersion.getMajor(), osVersion.getMinor(), osVersion.getMicro());
                    Version version11_3 = Version.newBuilder().setMajor(11).setMinor(3).build();
                    Version version13_7 = Version.newBuilder().setMajor(13).setMinor(7).build();
                    Version version14_5 = Version.newBuilder().setMajor(14).setMinor(5).build();
                    if (WorkerUtils.versionCompare(osVersion, version11_3) >= 0
                            && WorkerUtils.versionCompare(osVersion, version13_7) <= 0 ) {
                        //iOS SDK version 11.3 ~ 13.7,使用skan1.0版本
                        skanVersion = "1.0";
                        skad.setVersion(skanVersion);
                        String CTASignature = GenerateSignUtil.generateSign(skad, skanVersion);
                        skad.setSignature(CTASignature);
                    } else if (WorkerUtils.versionCompare(osVersion, version13_7) > 0) {
                        if (WorkerUtils.versionCompare(osVersion, version14_5) < 0) {
                            skanVersion = "2.0";
                            skad.setVersion(skanVersion);
                            //ios13.7以及ios14.5以下
                            //skan2.1版本 产生CTA秘钥
                            String CTASignature = GenerateSignUtil.generateSign(skad, skanVersion);
                            skad.setSignature(CTASignature);
                        } else {
                            //ios14.5以及ios14.5以上
                            //skan2.1版本 产生CTA VTA两个秘钥
                            skanVersion = "2.2";
                            skad.setVersion(skanVersion);
                            skad.setFidelityType("1");
                            String CTASignature = GenerateSignUtil.generateSign(skad, skanVersion);
                            skad.setFidelityType("0");
                            String VTASignature = GenerateSignUtil.generateSign(skad, skanVersion);
                            skad.setSignature(CTASignature);
                            skad.setVtaSignature(VTASignature);
                        }
                    }
                }
            }
            if (winAd.getSaturnChannelId().equals(SaturnDsp.GDT_CUSTOM_CHANNEL_ID)) {
                formatBuilder.adName("GDT");
                formatBuilder.allTrackers(ObjectMapperUtils.toJSON(runTimeContext.getAdRunTimeInfo().getGdtEventTrackers()));
            } else {
                formatBuilder.allTrackers("");
                formatBuilder.adName("");
            }
            formatBuilder.skanVersion(Optional.ofNullable(skad.getVersion()).orElse(""));
            formatBuilder.network(Optional.ofNullable(skad.getNetwork()).orElse(""));
            formatBuilder.campaign(Optional.ofNullable(skad.getCampaign()).orElse(""));
            formatBuilder.nonce(Optional.ofNullable(skad.getNonce()).orElse(""));
            formatBuilder.sourceapp(Optional.ofNullable(skad.getSourceapp()).orElse(""));
            formatBuilder.itunesitem(Optional.ofNullable(skad.getItunesitem()).orElse(""));
            formatBuilder.timestamp(Optional.ofNullable(skad.getTimestamp()).orElse(""));
            formatBuilder.ctaSignature(Optional.ofNullable(skad.getSignature()).orElse(""));
            formatBuilder.vtaSignature(Optional.ofNullable(skad.getVtaSignature()).orElse(""));
            formatBuilder.skad(skad);
            formatBuilder.threshold(Optional.ofNullable(runTimeContext.getActionThreshold()).orElse("0"));
            formatBuilder.thresholdType(Optional.ofNullable(runTimeContext.getActionType()).orElse(0));
            formatBuilder.inmobiVendor(Optional.ofNullable(runTimeContext.getInmobiVendor()).orElse(""));
            runTimeContext.setFormatterModel(formatBuilder.build());
            LogUtils.logInfo(runTimeContext.getRequestId(),"formatterModel {}",formatBuilder.build());
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
        return Mono.just(0);
    }

    private void setDownloadInfo(RunTimeContext runTimeContext, FormatterModel.FormatterModelBuilder formatBuilder) {
        //下载五要素
        Ad winAd = runTimeContext.getWinAd();
        OsType osType = runTimeContext.getDevice().getOsType();
        MaterialMeta creative = winAd.getCreative();
        String packageName = creative.getPackageName();

        AppInfoBaseModel appInfoBaseModel = null;
        if (StringUtils.isNotEmpty(packageName)) {
            appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPackageName(packageName, osType.getNumber());
        }
        if (Objects.isNull(appInfoBaseModel) && runTimeContext.getWinBidResponse().getSourceType()==SourceType.SATURN_VALUE) {
            appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPublisherId(winAd.getSaturnChannelId(), osType.getNumber());
        }
        if (Objects.isNull(appInfoBaseModel) && runTimeContext.getWinBidResponse().getSourceType()==SourceType.PERF_VALUE) {
            appInfoBaseModel = appInfoBaseRepo.getAppInfoBaseModelByPublisherId(String.valueOf(winAd.getAdvAccId()), osType.getNumber());
        }
        if (!Objects.isNull(appInfoBaseModel)) {
            String key = String.valueOf(appInfoBaseModel.getType());
            if (appInfoBaseModel.getType() == 0) {
                key +=appInfoBaseModel.getIdentifier();
            }
            else {
                key+=appInfoBaseModel.getPackageName();
            }
            key += appInfoBaseModel.getOs();
            String permissionLink = permissionLinkPrefix + DigestUtils.md5DigestAsHex(key.getBytes(StandardCharsets.UTF_8)).toUpperCase()+".html";
            formatBuilder.packageName(appInfoBaseModel.getPackageName())
                    .appName(appInfoBaseModel.getAppName())
                    .publisherName(appInfoBaseModel.getPublisherName())
                    .version(appInfoBaseModel.getVersion())
                    .privacyLink(appInfoBaseModel.getPrivacyLink())
                    .permissionLink(permissionLink)
                    .packSize(appInfoBaseModel.getPackSize());
            String appFunctionDesc = appInfoBaseModel.getAppFunctionDesc();
            if(StringUtils.isNotBlank(appFunctionDesc)){
                String appFunctionDescLink = appDescPrefix + DigestUtils.md5DigestAsHex(key.getBytes(StandardCharsets.UTF_8)).toUpperCase()+".html";
                formatBuilder.appFunctionDesc(appFunctionDescLink);
            }
        }
        else {
            // 没匹配到的打日志
            if (StringUtils.isNotEmpty(packageName)) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "there is not download factor , packageName: {}, os: {}",packageName, osType.getNumber());
            }
            else if (runTimeContext.getWinBidResponse().getSourceType()==SourceType.SATURN_VALUE) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "there is not download factor , saturnChannelId: {}, os: {}", winAd.getSaturnChannelId(), osType.getNumber());
            }
            else if (runTimeContext.getWinBidResponse().getSourceType()==SourceType.PERF_VALUE) {
                LogUtils.logInfo(runTimeContext.getRequestId(),"there is not download factor , advAccId: {}, os: {}", winAd.getAdvAccId(), osType.getNumber());
            }
        }
        //dsp 返回优先
        if(StringUtils.isNotBlank(creative.getPackageName())){
            formatBuilder.packageName(creative.getPackageName());
        }
        if(StringUtils.isNotBlank(creative.getAppName())){
            formatBuilder.appName(creative.getPackageName());
        }
        if(StringUtils.isNotBlank(creative.getPublisherName())){
            formatBuilder.publisherName(creative.getPublisherName());
        }
        if(StringUtils.isNotBlank(creative.getAppVersion())){
            formatBuilder.version(creative.getAppVersion());
        }
        if(StringUtils.isNotBlank(creative.getPrivacyLink())){
            formatBuilder.privacyLink(creative.getPrivacyLink());
        }
        if(StringUtils.isNotBlank(creative.getPermissionLink())){
            formatBuilder.permissionLink(creative.getPermissionLink());
        }
        if(StringUtils.isNotBlank(creative.getFeatureLink())){
            formatBuilder.appFunctionDesc(creative.getFeatureLink());
        }
        if(creative.getProductSize() != 0){
            formatBuilder.packSize(creative.getProductSize());
        }
    }


    private Map<String, List<String>> convertEventTrackerMap(Map<TrackingEvent, List<String>> trackerMap) {
        Map<String, List<String>> eventTracker = new HashMap<>();
        if (MapUtils.isNotEmpty(trackerMap)) {
            List<String> trackers;
            for (TrackingEvent trackingEvent : trackerMap.keySet()) {
                trackers = trackerMap.getOrDefault(trackingEvent, new ArrayList<>());
                if (CollectionUtils.isNotEmpty(trackers)) {
                    List<String> finalTrackers = trackers;
                    Arrays.stream(TrackerEventInfo.values())
                            .filter(TrackerEventInfo::isApiParamTracker)
                            .forEach(trackerEventInfo -> {
                                if (trackingEvent == trackerEventInfo.getTrackingEvent()) {
                                    eventTracker.put(trackerEventInfo.getEventKey(), finalTrackers);
                                }
                            });
                }
            }
        }
        return eventTracker;
    }

    private String getEncryptedBid(String bid, RunTimeContext runTimeContext,FormatterModel.FormatterModelBuilder builder) throws SunException {
        GoaRtt goaRtt = runTimeContext.getGoaRtt();
        if (StringUtils.isBlank(goaRtt.getSkdm()) || StringUtils.isBlank(goaRtt.getSkdv())) {
            //don't need to encrypt bid cause Empty of Skdv and Skdm param
            return bid;
        }
        byte[] encryptedBytes;
        try {
            SecureSession session = new SecureSession(goaRtt.getSkdm().getBytes(StandardCharsets.UTF_8), goaRtt.getPrivateKey(), true);
            encryptedBytes = session.write(bid.getBytes(Charsets.UTF_8));
            // todo 设置是否加密bid成功字段 hasBidEncrypt
            builder.hasBidEncrypt(true);
            return new String(encryptedBytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
//            log.error("getEncryptedBid error", e);
            throw new SunException(TermCode.BAD_SEC_REQ, runTimeContext);
        }
    }
}