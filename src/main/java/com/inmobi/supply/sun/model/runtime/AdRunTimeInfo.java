package com.inmobi.supply.sun.model.runtime;

import cn.inmobi.pb.api.TrackingEvent;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdRunTimeInfo
 * @author remi.li
 */
@Data
public class AdRunTimeInfo {
    private String beaconUrl;

    private String clickUrl;

    /**
     * impression tracker urls list
     */
    private List<String> impressionTrackers;

    /**
     * click tracker urls list
     */
    private List<String> clickTrackers;
    /**
     * win notice tracker urls
     */
    private List<String> winNoticeTrackers;

    /**
     * <TrackingEvent, trackerUrls>
     */
    private Map<TrackingEvent, List<String>> eventTrackers = new HashMap<>();
    private Map<TrackingEvent, List<String>> gdtEventTrackers = new HashMap<>();
}
