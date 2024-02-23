package com.inmobi.supply.sun.utils;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public class YidianPriceEncryptUtils {

    private static final String PRICE_MACROS = "${PRICE}";
    private static final String SECRTE_KEY = "hKfWUcugZ4yjUB3ybVAF6Gzb9BlbjHKi";
    private static final String BASE64 = "_fhVZqKQLb3OkW7l2t-Caz4IyJuPd10BsMcXngeAREYimGD9Nowr5Tx6UpjvSFH8";

    public static String yidianEncodedPrice(String price, String ext) {

        if (ext.contains(PRICE_MACROS)) {
            String clearText = StringUtils.replace(ext, PRICE_MACROS, price);
            String ck = toHmacSha1(clearText, SECRTE_KEY);

            clearText += "&ck=" + ck;
            String priceEncode = toBase64(clearText, BASE64);
            return priceEncode;
        } else {
            return "";
        }

    }

    public static String toHmacSha1(String encryptText, String encryptKey) {
        try {
            //根据给定的字节数组构造一个密钥,第二参数指定一个密钥算法的名称
            SecretKey secretKey = new SecretKeySpec(encryptKey.getBytes(), "HmacSHA1");
            //生成一个指定 Mac 算法 的 Mac 对象
            Mac mac = Mac.getInstance("HmacSHA1");
            //用给定密钥初始化 Mac 对象
            mac.init(secretKey);

            byte[] text = encryptText.getBytes();
            //完成 Mac 操作
            byte[] bytes = mac.doFinal(text);
            return byte2String(bytes);
        } catch (Exception e) {
            System.out.println("error is " + e);
            return null;
        }
    }

    private static String byte2String(byte[] bytes) {
        StringBuilder sb = new StringBuilder(40);
        for (byte x : bytes) {
            if ((x & 0xff) >> 4 == 0) {
                sb.append("0").append(Integer.toHexString(x & 0xff));
            } else {
                sb.append(Integer.toHexString(x & 0xff));
            }
        }
        return sb.toString();
    }

    public static String toBase64(String text, String key) {

        String encodedText = null;
        try {
            final byte[] textByte = text.getBytes("UTF8");
            char[] toBase64 = key.toCharArray();
            encodedText = new String(encode(textByte, toBase64), "UTF8");

        } catch (Exception e) {
            System.out.println("error is " + e);
        }
        return encodedText;
    }

    public static byte[] encode(byte[] src, char[] toBase64) {
        int len = outLength(src.length);          // dst array size
        byte[] dst = new byte[len];
        int ret = encode0(src, 0, src.length, dst, toBase64);
        if (ret != dst.length)
            return Arrays.copyOf(dst, ret);
        return dst;
    }

    private static final int outLength(int srclen) {
        int len = 0;

        int n = srclen % 3;
        len = 4 * (srclen / 3) + (n == 0 ? 0 : n + 1);
        return len;
    }

    private static int encode0(byte[] src, int off, int end, byte[] dst, char[] toBase64) {
        char[] base64 = toBase64;
        int sp = off;
        int slen = (end - off) / 3 * 3;
        int sl = off + slen;
        int dp = 0;
        while (sp < sl) {
            int sl0 = Math.min(sp + slen, sl);
            for (int sp0 = sp, dp0 = dp; sp0 < sl0; ) {
                int bits = (src[sp0++] & 0xff) << 16 |
                        (src[sp0++] & 0xff) << 8 |
                        (src[sp0++] & 0xff);
                dst[dp0++] = (byte) base64[(bits >>> 18) & 0x3f];
                dst[dp0++] = (byte) base64[(bits >>> 12) & 0x3f];
                dst[dp0++] = (byte) base64[(bits >>> 6) & 0x3f];
                dst[dp0++] = (byte) base64[bits & 0x3f];
            }
            int dlen = (sl0 - sp) / 3 * 4;
            dp += dlen;
            sp = sl0;

        }
        if (sp < end) {               // 1 or 2 leftover bytes
            int b0 = src[sp++] & 0xff;
            dst[dp++] = (byte) base64[b0 >> 2];
            if (sp == end) {
                dst[dp++] = (byte) base64[(b0 << 4) & 0x3f];
            } else {
                int b1 = src[sp++] & 0xff;
                dst[dp++] = (byte) base64[(b0 << 4) & 0x3f | (b1 >> 4)];
                dst[dp++] = (byte) base64[(b1 << 2) & 0x3f];

            }
        }
        return dp;
    }
}
