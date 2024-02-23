package com.inmobi.supply.sun.model.getui;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author zeki.zhao
 */
@Data
public class GeTuiTaskListRequest {
    private String os;

    private String userCode;

    private String imeiMD5;
    private String macMD5;
    private String idfaMD5;
    private String oaidMd5;
    private String type;
    // 预留字段，用来记录本次Md5关键请求id的值

    private Map<String, List<String>> projectTaskList;

    public String getfirstMd5() {
        if (os.equalsIgnoreCase("android")){
            if (StringUtils.isNotBlank(oaidMd5)){
                return oaidMd5;
            }else if(StringUtils.isNotBlank(imeiMD5)){
                return imeiMD5;
            }
        } else {
            return idfaMD5;
        }
        return "";
    }
}
