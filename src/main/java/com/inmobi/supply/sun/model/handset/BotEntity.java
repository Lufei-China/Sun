package com.inmobi.supply.sun.model.handset;

public class BotEntity extends HandsetEntity {

    private String externalId;

    public BotEntity(){
    }

    public BotEntity(String id){
        this.externalId = id;
    }

    @Override
    public boolean isBot() {
        return true;
    }

    @Override
    public String getId() {
        return externalId;
    }
}
