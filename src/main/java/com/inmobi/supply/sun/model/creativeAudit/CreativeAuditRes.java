package com.inmobi.supply.sun.model.creativeAudit;

import lombok.Data;

import java.util.Date;

/**
 * @author zeki.zhao
 */
@Data
public class CreativeAuditRes {

    private Date date;
    private String publisherId;
    private String advertiserId;
    private String creativeId;
    private String adapterCreativeId;
}
