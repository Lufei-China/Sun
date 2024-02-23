package com.inmobi.supply.sun.model.redis;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zeki.zhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeTuiProjectTask implements Serializable {
    private String projectId;
    private String taskId;
    private String csid;
    private List<Long> placementIds;
}
