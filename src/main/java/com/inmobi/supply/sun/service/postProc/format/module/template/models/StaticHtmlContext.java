package com.inmobi.supply.sun.service.postProc.format.module.template.models;

import com.inmobi.supply.component.model.adFormat.Image;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author neilnandi @Inmobi.
 */

@Data
@AllArgsConstructor
@Builder
public class StaticHtmlContext {

    private String sdkVersion;
    private String beaconUrl;

    private List<String> impressionTrackers;
    private List<String> clickTrackers;
    private String mraidPath;
    private String optOutUrl;

    private boolean isAndroid;
    private boolean isIOS;
    private boolean isSDK;
    private boolean isInterstitial;

    // Settings
    private boolean centreCreative;

    // Info for LandingPage
    private String advertiserId;
    private String creativeId;
    private String userAgent;
    private String userIPv4;

    //Ad Markup
    private Image image;
    private int deviceScreenHeight;
    private int deviceScreenWidth;
    private String targetUrl;

  private String title;
  private String description;
  private String iconUrl;
  private String ctaText;

  private String popUpText;
  private String skipText;
  private String continueText;

  private boolean isShouldCheckForMraidOnBodyLoad;
  private boolean  isDisableCreativeResize;
  private boolean buildMorphAdMarkup;
  private boolean isSdkSupportsNativeTracking;
  private int adHeight;
  private int adWidth;
  //allow h5 open deeplink
  private boolean allowAutoRedirection;

}
