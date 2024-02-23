package com.inmobi.supply.sun.constant;


public enum BidDropReason {
    VALID(false),
    BID_EMPTY(false),
    AD_EMPTY(false),
    SOURCE_TYPE_ERROR(false),
    AD_INVALID(false);

    private boolean debug;

    BidDropReason(boolean debug) {
    }

    public boolean log(){
        return this.debug;
    }
}