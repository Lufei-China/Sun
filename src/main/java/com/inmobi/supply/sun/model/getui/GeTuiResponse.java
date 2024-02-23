package com.inmobi.supply.sun.model.getui;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 功能概述
 * className:      GeTuiResponse
 * package:        com.inmobi.supply.sun.model.getui
 * author:         Gavin.Xu
 * date:           2022/1/26
 */
@Data
public class GeTuiResponse {
    private String code;
    private String msg;
    private Map<String,List<String>> data;
}
