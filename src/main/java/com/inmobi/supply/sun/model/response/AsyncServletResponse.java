package com.inmobi.supply.sun.model.response;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.servlet.http.Cookie;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * AsyncServletResponse use to build final response
 *
 * @author remi.li
 */
@Data
public class AsyncServletResponse {
    private HttpStatus status;
    private Map<String, String> headers = Maps.newHashMap();
    private byte[] responseBody;
    private String responseBodyStr;
    private MediaType contentType;
    private Charset characterEncoding;
    private String statusMessage;
    private List<Cookie> cookies;
    private Long placementId;
    private int termCode;
    private boolean isBaidu;
    private String adName;
    private String channel;
    private String requestId;

    public void setHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public void setStatusAndMessage(HttpStatus code, String message) {
        this.status = code;
        this.statusMessage = message;
    }

    public void addCookie(Cookie cookie) {
        if (cookies == null) {
            this.cookies = Lists.newArrayList();
        }
        this.cookies.add(cookie);
    }
}
