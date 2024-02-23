package com.inmobi.supply.sun.envelop.test;

import cn.inmobi.pb.EventRR;
import cn.inmobi.pb.api.DeviceId;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class TrackerTest {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final String HMAC_SHA1_SECRET_KEY = "MFH0slwW3Q6QGWMzM998Kg9hUKIAKujd";

    String trackerHost = "http://127.0.0.1:8000";

    String IMPR_CONTEXT = "ad_display";
    String CLICK_CONTEXT = "ad_click";



    @Test
    public void TestImpressionUrl() {
        DeviceId deviceId = DeviceId.newBuilder()
                .setAndroidId("androidId123456")
                .build();
        EventRR eventRR = EventRR.newBuilder().
                setEventType(123)
                .setRequestId("uuuusjjdkflglhglncvnfj")
                .setAppId("999999")
                .setDeviceId(deviceId)
                .build();
        byte[] bytes = eventRR.toByteArray();
        StringBuilder eventRRSb = new StringBuilder(Base64.getUrlEncoder().withoutPadding().encodeToString((bytes)));
        String mac = genHMAC("CiRlODlmM2ExNS1hNGI4LTQ3YmUtOGNlMC01YTUwMDcwOWY1MjMaDTE1MDk0MTM3NTIzOTkgEipMIhAxNjcxMDEwOTk5NDI2MDcxKg0xNDk5ODY4Nzk3MTg4MKA4ONYwWJ4VYiAwNWQ2NmJiZDJjOWQ0MzRjODczZTA0YWE5NDQxMWFjYTABOAFCDTE0OTk4Njg3OTcxODhajgFNb3ppbGxhLzUuMCAoTGludXg7IEFuZHJvaWQgNC40LjI7IFpBOTc3IEJ1aWxkL0tPVDQ5SCkgQXBwbGVXZWJLaXQvNTM3LjM2IChLSFRNTCwgbGlrZSBHZWNrbykgVmVyc2lvbi80LjAgQ2hyb21lLzMwLjAuMC4wIE1vYmlsZSBTYWZhcmkvNTM3LjM2cg0xNTk0NzIwNTMyNTIyiAEDkAEBmgFuSiA5ZDlkN2M4NzI2MGE3ZGJhMTQzYmJkYzAwNzU5MzEyMmogMzk3Nzk0MTMxNTZlN2YwMjYzMDRlYWI4NDg5YzA1NjZyKDUwYzljYWQ1MzkyMTQ3MzExZjhkODRiMzZhMDNlMDQ0ODc4ZDBiMzQ", HMAC_SHA1_SECRET_KEY);
        eventRRSb.append("/");
        eventRRSb.append(mac);
        eventRRSb.append("?");
        eventRRSb.append("ts=$TS");
        System.out.println(eventRRSb.toString());

        String imprUrl = trackerHost + "/" + IMPR_CONTEXT + "/" + eventRRSb.toString();
        String clickUrl = trackerHost + "/" + CLICK_CONTEXT + "/" + eventRRSb.toString();
        System.out.println(imprUrl);
        System.out.println(clickUrl);
        System.out.println(System.currentTimeMillis());


    }

    static String genHMAC(String data, String key) {
        String result = null;
        try {
            SecretKeySpec signinKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signinKey);
            byte[] rawHmac = mac.doFinal(data.getBytes());
            result = Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
            return result;
        } catch (NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        } catch (InvalidKeyException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }
}
