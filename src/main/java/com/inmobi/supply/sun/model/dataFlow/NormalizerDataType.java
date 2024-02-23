package com.inmobi.supply.sun.model.dataFlow;

import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.utils.IdGenerator;
import lombok.Data;

import java.util.Map;

@Data
public class NormalizerDataType extends DataType {
    private String ua;
    private String host;
    private String url;
    private String uri;
    private String path;
    private String realIp;
    private String forwardForIp;
    private String requestId;
    private IdGenerator.GUID guid;
    private Boolean https;
    private RequestType requestType;
    private Map<String,String> headers;
    private Map<String,String> inmobiHeaders;
}