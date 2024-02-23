package com.inmobi.supply.sun.model.getui;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author zeki.zhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeTuiProjectTask implements Serializable {

    private static final long serialVersionUID = 7274370249717553060L;
    private String projectId;
    private String taskId;
    private String csid;
    private String start;
    private String end;
}
