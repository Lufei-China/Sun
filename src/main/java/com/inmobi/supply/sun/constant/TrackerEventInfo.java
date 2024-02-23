package com.inmobi.supply.sun.constant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;

import cn.inmobi.pb.EventRR;
import cn.inmobi.pb.api.*;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.TrackerUrlConfig;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.HmacMd5CrcUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.UrlUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.*;

/**
 * TrackerEvent enum
 *
 * @author remi.li
 */
@Getter
@Slf4j
public enum TrackerEventInfo {
    //Api3.1 & 3.3 trackers
    CLICK_TRACKER_URLS(TrackingEvent.CLICK, "clickTrackers", "m=8&ts=$TS", true),
    IMPRESSION_TRACKER_URLS(TrackingEvent.VIEW, "impressionTrackers", "m=18&ts=$TS", true),
    WIN_NOTICE_TRACKER_URLS(TrackingEvent.WIN_NOTICE, "winNoticeTrackers", "m=28&ts=$TS", true),
    CLIENT_FILL(TrackingEvent.CLIENT_FILL, "clientFill", "m=120&ts=$TS", false),

    //deeplink
    DEEPLINK_ATTEMPT_TRACKER_URLS(TrackingEvent.DEEPLINK_ATTEMPT, "dplAttempt", "m=124&ts=$TS", true, InteractionType.DEEPLINK),
    DEEPLINK_SUCCESS_TRACKER_URLS(TrackingEvent.DEEPLINK_SUCCESS, "dplSuccess", "m=125&ts=$TS", true, InteractionType.DEEPLINK),
    FALLBACK_TRACKERS(TrackingEvent.FALLBACK_URL, "fallbackTrackers", "m=126&ts=$TS", true, InteractionType.DEEPLINK),

    //download
    DOWNLOAD_START_TRACKERS(TrackingEvent.DOWNLOAD_START, "downloadStart", "m=128&ts=$TS", true, InteractionType.DOWNLOAD),
    DOWNLOAD_FINISH_TRACKERS(TrackingEvent.DOWNLOAD_FINISH, "downloadFinish", "m=129&ts=$TS", true, InteractionType.DOWNLOAD),
    INSTALL_FINISH_TRACKERS(TrackingEvent.INSTALL_FINISH, "installFinish", "m=130&ts=$TS", true, InteractionType.DOWNLOAD),
    APP_INSTALLED_TRACKERS(TrackingEvent.APP_INSTALLED, "appInstalled", "m=131&ts=$TS", true, InteractionType.DOWNLOAD),
    DOWNLOADING(TrackingEvent.DOWNLOADING, "downloading", "m=91&ts=$TS", false, InteractionType.DOWNLOAD),
    DOWNLOADER_ERROR(TrackingEvent.DOWNLOADER_ERROR, "downloaderError", "m=93&ts=$TS", false, InteractionType.DOWNLOAD),

    //video trackers
    VIDEO_START(TrackingEvent.VIDEO_START, "videoStart", "m=10&ts=$TS", true, CreativeType.VIDEO),
    FIRST_QUARTILE(TrackingEvent.VIDEO_Q1, "firstQuartile", "m=121&ts=$TS", true, CreativeType.VIDEO),
    MIDPOINT(TrackingEvent.VIDEO_Q2, "midpoint", "m=122&ts=$TS", true, CreativeType.VIDEO),
    THIRD_QUARTILE(TrackingEvent.VIDEO_Q3, "thirdQuartile", "m=123&ts=$TS", true, CreativeType.VIDEO),
    VIDEO_COMPLETE(TrackingEvent.VIDEO_COMPLETE, "videoComplete", "m=13&ts=$TS", true, CreativeType.VIDEO),
    VIDEO_AUTO_PLAY(TrackingEvent.VIDEO_AUTO_PLAY, "videoAutoPlay", "m=127&ts=$TS", true, CreativeType.VIDEO),
    VIDEO_PAUSE(TrackingEvent.VIDEO_PAUSE, "videoPause", "m=132&ts=$TS", true, CreativeType.VIDEO),
    VIDEO_RESUME(TrackingEvent.VIDEO_RESUME, "videoResume", "m=133&ts=$TS", true, CreativeType.VIDEO),
    VIDEO_ALL(TrackingEvent.VIDEO_ALL, "videoAll", "m=138&ts=$TS", true, CreativeType.VIDEO),

    DOWN_X(TrackingEvent.DOWN_X, "downX", "m=134&ts=__DOWN_X__", true),
    DOWN_Y(TrackingEvent.DOWN_Y, "downY", "m=135&ts=__DOWN_Y__", true),
    UP_X(TrackingEvent.UP_X, "upX", "m=136&ts=__UP_X__", true),
    UP_Y(TrackingEvent.UP_Y, "upY", "m=137&ts=__UP_Y__", true),

    CTA_TRIGGER_FAIL(TrackingEvent.CTA_TRIGGER_FAIL, "ctaTriggerFail", "m=139&ts=$TS", true, OsType.IOS),
    VAT_TRIGGER_FAIL(TrackingEvent.VTA_TRIGGER_FAIL, "ctaTriggerFail", "m=140&ts=$TS", true, OsType.IOS),;

    TrackerEventInfo(TrackingEvent trackingEvent, String eventKey, String querySnippet, boolean apiParamTracker) {
        this.trackingEvent = trackingEvent;
        this.eventKey = eventKey;
        this.querySnippet = querySnippet;
        this.apiParamTracker = apiParamTracker;
        this.interactionType = InteractionType.UNKNOW_INTERACTION_TYPE;
        this.creativeType = CreativeType.UNKNOW_CREATIVE_TYPE;
        this.osType = OsType.UNKNOW_OS_TYPE;
    }

    TrackerEventInfo(TrackingEvent trackingEvent, String eventKey, String querySnippet, boolean apiParamTracker, InteractionType interactionType) {
        this.trackingEvent = trackingEvent;
        this.eventKey = eventKey;
        this.querySnippet = querySnippet;
        this.apiParamTracker = apiParamTracker;
        this.interactionType = interactionType;
        this.creativeType = CreativeType.UNKNOW_CREATIVE_TYPE;
        this.osType = OsType.UNKNOW_OS_TYPE;
    }

    TrackerEventInfo(TrackingEvent trackingEvent, String eventKey, String querySnippet, boolean apiParamTracker, CreativeType creativeType) {
        this.trackingEvent = trackingEvent;
        this.eventKey = eventKey;
        this.querySnippet = querySnippet;
        this.apiParamTracker = apiParamTracker;
        this.interactionType = InteractionType.UNKNOW_INTERACTION_TYPE;
        this.creativeType = creativeType;
        this.osType = OsType.UNKNOW_OS_TYPE;
    }

    TrackerEventInfo(TrackingEvent trackingEvent, String eventKey, String querySnippet, boolean apiParamTracker, OsType osType) {
        this.trackingEvent = trackingEvent;
        this.eventKey = eventKey;
        this.querySnippet = querySnippet;
        this.apiParamTracker = apiParamTracker;
        this.interactionType = InteractionType.UNKNOW_INTERACTION_TYPE;
        this.creativeType = CreativeType.UNKNOW_CREATIVE_TYPE;
        this.osType = osType;
    }

    private TrackingEvent trackingEvent;
    private String eventKey;
    private String querySnippet;
    private boolean apiParamTracker;
    private InteractionType interactionType;
    private CreativeType creativeType;
    private OsType osType;


    public static Flux<Tuple2<TrackingEvent, List<String>>> addBeaconUrl(EventRR eventRR, RunTimeContext runTimeContext) {
        TrackerUrlConfig beacon = NEW_BEACON.get();
        TrackerUrlConfig click = NEW_CLICK.get();
        TrackerUrlConfig wn = WIN_NOTICE.get();

        return Flux.fromIterable(Arrays.asList(TrackerEventInfo.values()))
                .publishOn(Schedulers.parallel())
                .flatMap((Function<TrackerEventInfo, Mono<Tuple2<TrackingEvent, List<String>>>>) eventInfo -> {
                    int interactionType = runTimeContext.getWinAd().getCreative().getInteractionType();
                    int creativeType = runTimeContext.getWinAd().getCreative().getCreativeType();
                    OsType osType = runTimeContext.getDevice().getOsType();
                    if (eventInfo.getInteractionType() != InteractionType.UNKNOW_INTERACTION_TYPE && eventInfo.getInteractionType().getNumber() == interactionType
                            || eventInfo.getCreativeType() != CreativeType.UNKNOW_CREATIVE_TYPE && eventInfo.getCreativeType().getNumber() == creativeType
                            || eventInfo.getOsType() != OsType.UNKNOW_OS_TYPE && eventInfo.getOsType() == osType
                            || eventInfo.getInteractionType() == InteractionType.UNKNOW_INTERACTION_TYPE && eventInfo.getCreativeType() == CreativeType.UNKNOW_CREATIVE_TYPE && eventInfo.getOsType() == OsType.UNKNOW_OS_TYPE) {
                        EventRR eventRRDiff = eventRR.toBuilder().setEventType(eventInfo.getTrackingEvent().getNumber()).build();
                        String encodeToString = Base64.getUrlEncoder().withoutPadding().encodeToString(eventRRDiff.toByteArray());
                        if(StringUtils.isNotBlank(eventRR.getUserIp()) && eventRR.getUserIp().equals(eventRR.getUserIpv6())){
                            LogUtils.logInfo(runTimeContext.getRequestId(),"ipIsEqual encodeToString:{}",encodeToString);
                        }

                        //String eventRRCrc = createTrackUrlSuffix(encodeToString);
                        String key = getCheckKey(encodeToString);

                        String beaconUrl = createUrl(beacon);
                        String clickUrl = createUrl(click);
                        String wnUrl = createUrl(wn);

                        List<String> trackers = new ArrayList<>();
                        RequestType requestType = runTimeContext.getRequestType();
                        TraceUrlApi.generateViewUrl(runTimeContext, eventInfo, trackers);
                        if (eventInfo == TrackerEventInfo.CLICK_TRACKER_URLS) {
                            trackers.add(clickUrl
                                    + "?s=" + encodeToString
                                    + "&v=" + key
                                    + "&" + eventInfo.querySnippet
                                    //+ "&id=" + runTimeContext.getPlacementId()
                                    //+ "&a=" + adId
                                    + MacroConstant.getMacroStringByTrackingEvent(TrackingEvent.CLICK));
                        } else {
                            boolean bidShare = Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1;
                            if (eventInfo == TrackerEventInfo.IMPRESSION_TRACKER_URLS) {
                                if (bidShare){
                                    trackers.add(beaconUrl
                                            + "?s=" + encodeToString
                                            + "&v=" + key
                                            + "&" + eventInfo.querySnippet
                                            + MacroConstant.getMacroStringByTrackingEvent(TrackingEvent.VIEW));
                                } else {
                                    trackers.add(beaconUrl
                                            + "?s=" + encodeToString
                                            + "&v=" + key
                                            + "&" + eventInfo.querySnippet);
                                }
                            } else if (eventInfo == TrackerEventInfo.WIN_NOTICE_TRACKER_URLS && bidShare){
                                String winNotice = wnUrl
                                        + "?s=" + encodeToString
                                        + "&v=" + key
                                        + "&" + eventInfo.querySnippet
                                        + MacroConstant.getMacroStringByTrackingEvent(TrackingEvent.WIN_NOTICE);
                                LogUtils.logInfo(runTimeContext.getRequestId(), "wnUrl {}", winNotice);
                                if (requestType == RequestType.CUSTOM_API) {
                                    runTimeContext.getBidSharingWinNotices().add(winNotice);
                                } else {
                                    trackers.add(winNotice);
                                }
                            } else if (eventInfo == TrackerEventInfo.VIDEO_ALL) {
                                if (!requestType.isSdk() && requestType.isAbove33()) {
                                    //ATTENTION！高版本支持
                                    trackers.add(beaconUrl
                                            + "?s=" + encodeToString
                                            + "&v=" + key
                                            + "&" + eventInfo.querySnippet
                                            + MacroConstant.getMacroStringByCreativeType(CreativeType.VIDEO));
                                }
                            } else {
                                if (eventInfo == TrackerEventInfo.CTA_TRIGGER_FAIL || eventInfo == TrackerEventInfo.VAT_TRIGGER_FAIL) {
                                    if (runTimeContext.getBidRequest().getAdslots(0).getSupportSkan()
                                            && runTimeContext.getWinAd().getIsSkan()) {
                                        //支持skan曝光才会增加tracker
                                        trackers.add(beaconUrl
                                                + "?s=" + encodeToString
                                                + "&v=" + key
                                                + "&" + eventInfo.querySnippet
                                        );
                                    }
                                } else {
                                    trackers.add(beaconUrl
                                            + "?s=" + encodeToString
                                            + "&v=" + key
                                            + "&" + eventInfo.querySnippet
                                    );
                                }
                            }
                        }
                        LogUtils.logInfo(runTimeContext.getRequestId(),"event {} urls {}", eventInfo.getTrackingEvent(), ObjectMapperUtils.toJSON(trackers));
                        return Mono.just(Tuples.of(eventInfo.getTrackingEvent(), trackers));
                    } else {
                        return Mono.just(Tuples.of(eventInfo.getTrackingEvent(), new ArrayList<>()));
                    }
                });
    }

    public static String createUrl(TrackerUrlConfig trackerUrlConfig) {
        return trackerUrlConfig.getDomain() + SymbolString.URL_PATH_SEP + trackerUrlConfig.getPrefix();
    }

    public static String createWNUrl(TrackerUrlConfig trackerUrlConfig, String suffix) {
        return trackerUrlConfig.getDomain() + SymbolString.URL_PATH_SEP + trackerUrlConfig.getPrefix() + suffix;
    }

    private static String createTrackUrlSuffix(String encodeToString) {
        StringBuilder suffix = new StringBuilder();
        suffix.append(SymbolString.URL_PATH_SEP);
        suffix.append(encodeToString);
        suffix.append(SymbolString.URL_PATH_SEP);
        String url = suffix.toString();
        suffix.append(HmacMd5CrcUtils.sign(url));
        return suffix.toString();
    }

    private static String getCheckKey(String encodeToString) {
        StringBuilder suffix = new StringBuilder();
        suffix.append(SymbolString.URL_PATH_SEP);
        suffix.append(encodeToString);
        suffix.append(SymbolString.URL_PATH_SEP);
        String url = suffix.toString();
        return HmacMd5CrcUtils.sign(url);
    }

    /*
     * 生成替换曝光和点击事件的上报url，内部类为了考虑以后扩展一个大类或枚举和其他私有方法进行区分
     * 创建新的监测url 进行宏替换
     * */
    static class TraceUrlApi {
        public static final String ACCESS_KEY = "ieasyorg=50c883f5d4de461dba3ea0c44a810f7f";
        public static final String VIEW_URL = "https://collect.ieasy123.com/rec/simp?" + ACCESS_KEY;
        public static final String CLICK_URL = "https://collect.ieasy123.com/rec/sclick?" + ACCESS_KEY;

        static void generateViewUrl(RunTimeContext runTimeContext, TrackerEventInfo eventInfo, List<String> trackers) {
            // 判断naap是否需要追踪
            if (!runTimeContext.isNeedTraceApi()) {
                return;
            }
            // 判断是否为点击或曝光事件
            if (eventInfo != TrackerEventInfo.CLICK_TRACKER_URLS && eventInfo != TrackerEventInfo.IMPRESSION_TRACKER_URLS) {
                return;
            }
            StringBuffer sb = new StringBuffer();
            StringBuffer sb2 = new StringBuffer();
            if (eventInfo == TrackerEventInfo.CLICK_TRACKER_URLS) {
                sb.append(CLICK_URL);
            } else {
                sb.append(VIEW_URL);
            }
            sb2.append("&rid=").append(Optional.ofNullable(runTimeContext.getRequestId()).orElse(""))
                    .append("&ua=").append(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""))
                    .append("&ip=").append(StringUtils.isNotBlank(runTimeContext.getUserIpV4()) ? runTimeContext.getUserIpV4() : runTimeContext.getUserIpV6())
                    .append("&app_package=").append(Optional.ofNullable(null != runTimeContext.getAppModel() ? runTimeContext.getAppModel().getBundleId() : "").orElse(""))
                    .append("&channel=").append(Optional.ofNullable(null != runTimeContext.getAppModel() ? runTimeContext.getAppModel().getPubAccountId() : "").orElse(""))
                    .append("&app_name=").append(Optional.ofNullable(null != runTimeContext.getAppModel() ? runTimeContext.getAppModel().getAppName() :"").orElse(""));
            try {
                trackers.add(sb + URLEncoder.encode(sb2.toString(), "utf-8"));
//                log.info("{},startGenerateResult=" + trackers.get(0), runTimeContext.getPlacementId());
            } catch (Exception e) {
                log.error("TraceUrlApi generateViewUrl encode error", e);
                return;
            }
        }

    }
}