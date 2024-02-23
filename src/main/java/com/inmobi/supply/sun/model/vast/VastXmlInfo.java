package com.inmobi.supply.sun.model.vast;

/**
 * @author neilnandi @Inmobi.
 */

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VastXmlInfo {
    String adId;
    String endCardImageUrl;
    String adSystem;
    String title;
    String errorUrl;
    String impressionUrl;
    String duration;
    String start;
    String q1;
    String mid;
    String q3;
    String complete;
    String skip;
    String clickThrough;
    int videoWidth;
    int videoHeight;
    String videoUrl;
    int companionWidth;
    int companionHeight;
    String htmlResource;
    String trackingCreativeView;
    String companionClickThrough;
    String clickTracking;
}
