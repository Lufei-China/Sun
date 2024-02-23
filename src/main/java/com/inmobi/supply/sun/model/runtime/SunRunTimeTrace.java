package com.inmobi.supply.sun.model.runtime;

import lombok.Data;

@Data
public class SunRunTimeTrace {
    private String requestId;
    private String bundle;
    private int requestType;
    private long pe;
    private Long placementId;
    private int termCode;
    private long demondBidPrice;
    private long demondCpcPrice;
    private int bidType;
    private String demondNetDeduction;
    private int demondAuctionStatus;
    private String demondFloor;
    private String demondAdId;
    private long demondAccId;
    private long demondCampId;
    private long demondGroupId;
    private long demondBidType;
    private long timestamp;
    private long baiduAppId;
    private String pubAccount;
    private long floor;
    private long rrCharge;
    private long rrPay;
    private long rrFirstPrice;
    private long rrSecodnPrice;
    private int rrRevenueShareType;
    private String rrNetDeduction;
    private String rrAdpoolDeduction;
    private int rrSettleType;
    private long saturnBidPrice;
    private String saturnNetDeduction;
    private int saturnAuctionStatus;
    private String saturnFloor;
    private String saturnSegmentId;
    private String saturnChannelId;
    private int saturnBidType;
    private int winner;
    private String deepLink;
    private long usedTime;
    private int renderType;
}
