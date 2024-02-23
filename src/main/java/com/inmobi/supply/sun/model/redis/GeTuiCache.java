package com.inmobi.supply.sun.model.redis;

import lombok.Data;

import java.util.Map;

/**
 * 功能概述
 * className:      GeTuiCache
 * package:        com.inmobi.supply.sun.model.redis
 * author:         Gavin.Xu
 * date:           2022/7/22
 */
@Data
public class GeTuiCache {
    /**
     * 通投的个推任务标识
     */
    public static final String ALL = "all";
    private String placementId;
    private Map<String,GeTuiProjectTask> mappingTaskList;
}
