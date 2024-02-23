package com.inmobi.supply.sun.service.postProc.format.module.template.models;

/** @author neilnandi @Inmobi. */
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class AdUnitContext {
  @NonNull private final String sdkVersion;
  private final int supplyWidth;
  private final int supplyHeight;
  private final boolean displayDaaWidget;
  private final boolean secure;
  private final int maxPodDuration;
  private final String prefix;
  private final String podPrefix;
  private final String suffix;
  private final Object incentiveJsonObject;
  private final Object sitePreferencesJson;
  private final Object requestJson;
  private final List<CreativeContext> creativeList;
  private final short closeButtonSize;
  private final short ctrFactor;
  private final Set<Integer> supplyCapabilities;
  private final boolean rewarded;
  private final String adChoicesUrl;
  private final long adExpiryTime;
  private final boolean isVideo;
  private final double[] portraitSafeAreaContainer;
  private final double[] landscapeSafeAreaContainer;
  private final boolean iPhoneX;
  private final boolean safeAreaSDK;
  private final boolean vpaidDisabledByPublisher;
  private final Object skanMeta;
  private final boolean sdkAdPodRequest;
  private final Object skOverlayMeta;
  private final String mraidPath;
}
