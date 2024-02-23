package com.inmobi.supply.sun.model.handset;

public class HandsetExteranlEntity extends HandsetEntity {

    private String externalId;

    public HandsetExteranlEntity(String id) {
        this.externalId = id;
    }

    @Override
    public boolean isBot() {
        return false;
    }

    @Override
    public String getId() {
        return externalId;
    }
}
