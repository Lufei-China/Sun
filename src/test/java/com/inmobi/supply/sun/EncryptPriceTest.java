package com.inmobi.supply.sun;

import com.inmobi.supply.sun.constant.SaturnDsp;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 功能概述
 * className:      EncryptPriceTest
 * package:        com.inmobi.supply.sun
 * author:         Gavin.Xu
 * date:           2022/3/7
 */
public class EncryptPriceTest {

    /**
     * 原始价格字符串，采用 AES256ECB 模式，padding 方式为 PKCS5 加密
     */
    @Test
    public void kuaishouTest() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
//        String ak = "123456789abcdefghijklmnopqrstuvw";
//        String price = "1001";
//        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ak.getBytes(), "AES"));
//        byte[] bytes = cipher.doFinal(price.getBytes());
//        String settlePrice = new String(Base64.getEncoder().encode(bytes));
//        System.out.println(settlePrice);
//        System.out.println(URLEncoder.encode(settlePrice));

//                    byte[] initVector = crypto.createInitVector(System.currentTimeMillis(), 1L);
//                    String encrytedPrice = crypto.encodePriceValue(Double.valueOf(price), initVector);
//                    trackerUrl = StringUtils.replace(trackerUrl, macro, encrytedPrice);

                    String ak = "123456789abcdefghijklmnopqrstuvw";
                    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ak.getBytes(), "AES"));
                    String settlePrice = new String(Base64.getEncoder().encode(cipher.doFinal("1001".getBytes())));

        System.out.println(settlePrice);
        System.out.println(URLEncoder.encode(settlePrice));





    }
}
