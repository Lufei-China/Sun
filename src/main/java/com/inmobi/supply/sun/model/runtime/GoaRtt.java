package com.inmobi.supply.sun.model.runtime;

import lombok.*;

import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Collection;

/**
 * GoaRtt
 */
@Data
public class GoaRtt {
    private boolean goaTerminated = false;

    private String skdv; //Version: For Bid in both secure and non-secure case
    private String skdm; //Message: For Bid in both secure and non-secure case
    private RSAPrivateKey privateKey;

    //Init Worker: this will decide whether to make ads from SDK Cache or by calling adpools
    private Collection<CachedAdInfo> cachedAdInfos = new ArrayList<>();
    private boolean useCachedAd = false;

    //OpenAuctionReqAdapterFactory taskOuts
    private String impressionId; //This is the request Id from Google to be populated in Response for mapping between Req/Resp
    private long billingId; //Campaign Id(OpenRtb) corresponding to billing_id in GOA request
    private String sn; //In Goa all requests would be encrypted
    private String sm; // So we need to get sn and Sm from Goa Extension and put in RTT
    private double goaFloor; //Request level Floor coming from Google
    private String requestId; //Google's request Id -> parallel to taskId in UMP
    private String sdkId;

    private int deviceWidth = 0; //Device width
    private int deviceheight = 0; //Device Height
    private int bannerWidth = 0;  //Width in banner obj of BidRequest
    private int bannerHeight = 0; //Height in banner obj of BidRequest

    @Data
    static class CachedAdInfo {
        @NonNull
        private final String impressionId;
        @NonNull
        private String bid; //encrypted
        @NonNull
        private final CachedAdData cachedAdData;

        @Getter
        @Setter
        @ToString
        @AllArgsConstructor
        public static class CachedAdData {
            @NonNull
            private final String clickThroughUrl;
            private String impressionTrackingUrl;
            @NonNull
            private final String creativeId;

        }
    }
}
