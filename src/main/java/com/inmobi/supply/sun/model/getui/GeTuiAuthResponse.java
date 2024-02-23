package com.inmobi.supply.sun.model.getui;

import lombok.ToString;

/**
 * @author zeki.zhao
 */
@ToString
public class GeTuiAuthResponse {
    private String access_token = "default";

    private long expires_in;

    private String code = "-1";

    public boolean isExpired() {
        return System.currentTimeMillis() >= expires_in;
    }

    public void forceTokenExpiry() {
        expires_in = System.currentTimeMillis() - 1;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public void setExpires_in(long expires_in) {
        this.expires_in = System.currentTimeMillis() + expires_in * 1000 - 10000;
    }

    public String getAccess_token() {
        return access_token;
    }

    public long getExpires_in() {
        return expires_in;
    }

    public static void main(String[] args) {
        System.out.println(new GeTuiAuthResponse());
    }
}
