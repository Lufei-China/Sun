package com.inmobi.supply.sun.service.postProc.format.module.template.models;

/** @author neilnandi @Inmobi. */
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Builder
public class CreativeContext {
  private final String adImpressionId;

  // TODO. Banner template cleanup (This is part of creative json)
  private final String bannerUrl;
  private final String altText;

  private final String beaconUrl;
  private final String ns;
  // Prepend configured landing page
  private final String linkUrl;

  private final boolean supportsCreativeApi;
  private final String onTouchStartAction;
  private final String onTouchEndAction;
  private final String onClickAction;
  private final String prefix;
  private final String suffix;

  private final String target;
  private final String thirdPartyHtml;
  private final int width;
  private final int height;
  // TODO. Deprecate this and clean up banner templates
  private final boolean metricsEnabled;
  private final boolean firesBeaconUrl;
  private final boolean firesTpit;
  private final String trackingCodes;
  @Setter private Object jsonObject;
  private final String openingLandingUrl;
  private final Object cauElementJsonObject;
  private final Object appMetaJson;

  // This is added only for glance and should be used only by glance template.
  private final int glanceOpenMethod;
  // TODO. Find why this is used
  private final String clickServerUrl;
  // TODO. Update templates and move macro replacement back to ad pool
  private final boolean appInstall;
  private final String fallbackUrl;
  private final String landingPage;
  private final Set<Short> viewabilityPartnerListToEnableJs;
  private final String nativeDeeplinkLandingUrl;
  private final boolean omidEnabledOnAPI;
  private final String adChoicesUrl;
  private final String videoExperience;
}
