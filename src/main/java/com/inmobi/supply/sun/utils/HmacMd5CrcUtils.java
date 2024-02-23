package com.inmobi.supply.sun.utils;

import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * HmacMd5CrcUtils
 */
@Service
@Slf4j
public class HmacMd5CrcUtils {
    @Getter
    private static final String secretKeyVersionBase36 = "7";

    private static final String secretKey = "MFH0slwW3Q6QGWMzM998Kg9hUKIAKujd";
    private static final String algorithm = "HmacSHA1";

    private final static ThreadSafeMac mac = new ThreadSafeMac();

    private static class ThreadSafeMac extends ThreadLocal<Mac> {
        @Override
        public Mac initialValue() {
            Mac mac;
            SecretKeySpec sk;
            try {
                sk = new SecretKeySpec(secretKey.getBytes(), algorithm);
                mac = Mac.getInstance(algorithm);
                mac.init(sk);
                return mac;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static String sign(String s) {
        try {
            byte[] rawHmac = mac.get().doFinal(s.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Throwable e) {
            log.error("HmacMd5CrcUtils error", e);
            return "";
        }
    }
}