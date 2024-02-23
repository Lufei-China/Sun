package com.inmobi.supply.sun.model.common;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * @author zeki.zhao
 */
@Data
@Builder
public class HttpRequestEntity {

    private String endpoint;

    private byte[] bodyBytes;

    private Map<String, String> headers;

    private RequestMethod requestMethod;

    private long timeout;
}
