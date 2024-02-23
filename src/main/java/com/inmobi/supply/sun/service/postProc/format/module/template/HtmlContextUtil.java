package com.inmobi.supply.sun.service.postProc.format.module.template;

import static cn.inmobi.pb.api.TrackingEvent.CLICK;
import static cn.inmobi.pb.api.TrackingEvent.VIEW;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.ALLOW_AUTO_REDIRECTION;

import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.api.OsType;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.StaticHtmlContext;

public class HtmlContextUtil {

  public static final String HTML_CONTEXT = "htmlContext";
  public static final String TEMPLATE_TOOL = "tool";
  public static final String FORMATTER_MODEL = "model";
  public static final String STATIC_HTML_RESPONSE_TEMPLATE = "banner_int_static_html_response";
  public static final String STATIC_HTML_TEMPLATE = "banner_int_static_html";
  public static final String AD_CONTEXT = "ad";
  public static final String FIRST_AD_CONTEXT = "first";
  public static final String REWARDED_VIDEO_HTML_RESPONSE_TEMPLATE = "interstitial_video_html_response";
  public static final String BANNER_H5_HTML = "banner_h5_html";

  public static StaticHtmlContext buildHtmlContext(RunTimeContext runTimeContext, FormatterModel model) {
    return StaticHtmlContext.builder()
            .sdkVersion(model.getSdkVersion())
            .beaconUrl(runTimeContext.getAdRunTimeInfo().getBeaconUrl())
            .advertiserId(model.getAdId())
            .creativeId(model.getCreativeAuditId())
            .centreCreative(true)
            .clickTrackers(runTimeContext.getAdRunTimeInfo().getEventTrackers().get(CLICK))
            .impressionTrackers(runTimeContext.getAdRunTimeInfo().getEventTrackers().get(VIEW))
            .isAndroid(OsType.ANDROID == model.getOsType())
            .isIOS(OsType.IOS == model.getOsType())
            .isSDK(true)
            .isInterstitial(AdType.INTERSTITIAL == runTimeContext.getAdType())
            .mraidPath(
                    OsType.IOS == model.getOsType()
                            ? "http://cdn-supply.inmobi.cn/sdk/sdk/700/ios/mraid.js"
                            : "https://cdn-supply.inmobi.cn/sdk/sdk/700/android/mraid.js") //
            // .optOutUrl("sss")
            .userAgent(runTimeContext.getUserAgent())
            .userIPv4(runTimeContext.getUserIpV4())
            .image(model.getImage())
            .deviceScreenHeight(
                    AdType.INTERSTITIAL == runTimeContext.getAdType()
                            ? model.getDeviceScreenHeight()
                            : model.getImage().getHeight())
            .deviceScreenWidth(
                    AdType.INTERSTITIAL == runTimeContext.getAdType()
                            ? model.getDeviceScreenWidth()
                            : model.getImage().getWidth())
            .targetUrl(model.getTargetUrl())
            .iconUrl(model.getIconUrl())
            .title(model.getTitle())
            .description(model.getDescription())
            .optOutUrl("https://www.inmobi.com/page/opt-out")
            .build();
  }

  public static StaticHtmlContext buildHtmlContextVideo(RunTimeContext runTimeContext, FormatterModel model) {
    return StaticHtmlContext.builder()
            .popUpText("还未达到领奖时间，确认退出吗?")//Do you wish to skip rewards?
            .continueText("继续观看领取奖励")//Continue watching
            .skipText("坚持退出")//Skip
            .allowAutoRedirection(ALLOW_AUTO_REDIRECTION.get().contains(runTimeContext.getPlacementId()))
//            .popUpText("Do you wish to skip rewards?")//Do you wish to skip rewards?
//            .continueText("Continue watching")//Continue watching
//            .skipText("Skip")//Skip
            .build();
  }


}
