package com.inmobi.supply.sun.utils;

import cn.inmobi.pb.api.Ad;
import com.google.common.io.BaseEncoding;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.inmobi.supply.sun.constant.SaturnDsp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.inmobi.supply.sun.utils.WorkerUtils.bytesToLong;
import static com.inmobi.supply.sun.utils.WorkerUtils.longToBytes;
import static com.inmobi.supply.sun.utils.WorkerUtils.mergeBytes;

/**
 * dsp 价格加密工具类
 *
 * @author remi.li
 */
@Slf4j
public class PriceEncryptUtils {
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String TOUTIAO_DOUBLE_CLICK_FACTOR = "1234567891234567";

    private static final String JD_CUSTOM_ENCRYPTION_KEY = "OWFmNmM1MzkyODFhNDI4NzhiNjA2M2MzNDM1NTY1Zjg=";
    private static final String JD_CUSTOM_INTEGRITY_KEY = "OGYzMWZiZWYyMmZiNGZiNTk4YzhjMDVlYTkzZDQ1MzI=";

    private static final byte[] DIDI_E_KEY = "1a5387915303738631c304607d6979215488".getBytes();
    private static final byte[] DIDI_I_KEY = "166382c15f237bd63a4091e30a0756485362".getBytes();
    private static final byte[] KUAISHOU_I_KEY = "9f216febfce9e5175653cf77b716311b".getBytes();

    private static final String ALI_AES_SECRET = "02064f88401ebcf3";

    private static final String PDD_AES_SECRET = "8ece1dcf03cfe0c6";

    private static final String GDT_CUSTOM_RSA = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBALjMT+wA6DuUbhfoa6y048s5MXW+8F6nq6LsoaZ1cCuRt08KSFhgy0bjwujKVLKymgQRQQaFRHEjavi3Wwo/PocCAwEAAQ==";
    private static final String BXM_AES_SECRET = "949bcd3fc7d8bqw3";

    private static final String DSP360_ENCRYPT_KEY_IV = "6213FC1A2C51C632";

    private static final List<String> needEncryptDsps = new ArrayList<>(Arrays.asList(SaturnDsp.PDD_CHANNEL_ID, SaturnDsp.TOUTIAO_CHANNEL_ID, SaturnDsp.JD_CUSTOM_CHANNEL_ID, SaturnDsp.DSP_360_CHANNEL_ID));

    private static final List<String> PRICE_MACROS = new ArrayList<>(Arrays.asList("${AUCTION_PRICE}", "${auction_price}", "$%7Bauction_price%7D", "{AUCTION_PRICE}", "%7Bauction_price%7D"));

    private static final List<String> ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("${ENCRYPTED_PRICE}", "${encrypted_price}", "$%7Bencrypted_price%7D", "{ENCRYPTED_PRICE}", "%7Bencrypted_price%7D"));

    private static final List<String> ALI_ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("%%SETTLE_PRICE%%", "%%settle_price%%"));

    private static final List<String> YIDIAN_ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("${YDZX_AUCTION_PRICE}", "${ydzx_auction_price}", "$%7Bydzx_auction_price%7D"));

    private static final List<String> MTWM_ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("${MTWM_AUCTION_PRICE}", "${mtwm_auction_price}", "$%7mtwm_auction_price%7D"));

    private static final List<String> KUAISHOU_ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("${AUCTION_PRICE}"));

    private static final List<String> GDT_CUSTOM_ENCRYPTED_PRICE_MACROS = new ArrayList<>(Arrays.asList("__AUCTION_PRICE__"));

    private static final List<String> BXM_PG_RTB_CUSTOM_PRICE_MACROS = new ArrayList<>(Arrays.asList("${AUCTION_PRICE}"));


    private static DoubleClickCrypto.Price crypto;

    static {
        try {
            crypto = new DoubleClickCrypto.Price(new DoubleClickCrypto.Keys(
                    new SecretKeySpec(DIDI_E_KEY, HMAC_ALGORITHM),
                    new SecretKeySpec(DIDI_I_KEY, HMAC_ALGORITHM)));
        } catch (InvalidKeyException e) {
            crypto = null;
        }
    }

    public static String priceEncrypt(String dspChannelId, long price) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        if (dspChannelId.equals(SaturnDsp.PDD_CHANNEL_ID)) {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(PDD_AES_SECRET.getBytes(), "AES"));
            return new String(Base64.getEncoder().encode(cipher.doFinal(String.valueOf(price).getBytes())));
        }
        if (dspChannelId.equals(SaturnDsp.TOUTIAO_CHANNEL_ID)) {
            Mac ekeyMac = Mac.getInstance(HMAC_ALGORITHM);
            Mac ikeyMac = Mac.getInstance(HMAC_ALGORITHM);
            ekeyMac.init(new SecretKeySpec("1c3c324024e3457b8573fdf26d31a903".getBytes(), HMAC_ALGORITHM));
            ikeyMac.init(new SecretKeySpec("40ef1bdcbd7b410e1407ebdd0ed49574".getBytes(), HMAC_ALGORITHM));
            byte[] requestIdMac = ekeyMac.doFinal(TOUTIAO_DOUBLE_CLICK_FACTOR.getBytes());
            byte[] requestIdMacPart = Arrays.copyOfRange(requestIdMac, 0, Long.BYTES);
            long temp = bytesToLong(requestIdMacPart);
            temp = temp ^ price;
            byte[] sign = ikeyMac.doFinal(mergeBytes(longToBytes(price), TOUTIAO_DOUBLE_CLICK_FACTOR.getBytes()));
            sign = Arrays.copyOfRange(sign, 0, 4);
            byte[] finalMsg = mergeBytes(TOUTIAO_DOUBLE_CLICK_FACTOR.getBytes(), longToBytes(temp), sign);
            return java.util.Base64.getUrlEncoder().encodeToString(finalMsg);
        }
        if (dspChannelId.equals(SaturnDsp.JD_CUSTOM_CHANNEL_ID)) {
            JdCryptoUtils.Keys keys = new JdCryptoUtils.Keys(
                    new SecretKeySpec(Base64.getDecoder().decode(JD_CUSTOM_ENCRYPTION_KEY), HMAC_ALGORITHM),
                    new SecretKeySpec(Base64.getDecoder().decode(JD_CUSTOM_INTEGRITY_KEY), HMAC_ALGORITHM));
            JdCryptoUtils.Price crypto = new JdCryptoUtils.Price(keys);
            return crypto.encodePriceValue(price, null);
        }
        if (dspChannelId.equals(SaturnDsp.DSP_360_CHANNEL_ID)) {
            SecretKeySpec key = new SecretKeySpec(DSP360_ENCRYPT_KEY_IV.getBytes(), "AES");
            IvParameterSpec iv = new IvParameterSpec(DSP360_ENCRYPT_KEY_IV.getBytes());
            // PKCS5Padding比PKCS7Padding效率高，PKCS7Padding可支持IOS加解密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] encryptedData = cipher.doFinal(Long.toString(price).getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encodeHex(encryptedData, true));
        }

        return null;
    }

    public static boolean needEncryptDsp(String dspChannelId) {
        return needEncryptDsps.contains(dspChannelId);
    }

    public static String replaceMacroPrice(String trackerUrl, String price, String dspChannelId, Ad winAd) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        for (String macro : PRICE_MACROS) {
            if (StringUtils.equals(dspChannelId, SaturnDsp.KUAISHOU_CHANNEL_ID)) {
                if (trackerUrl.contains(macro)) {
                    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KUAISHOU_I_KEY, "AES"));
                    String settlePrice = new String(Base64.getEncoder().encode(cipher.doFinal(price.getBytes())));
                    trackerUrl = StringUtils.replace(trackerUrl, macro, URLEncoder.encode(settlePrice));
                }
            } else {
                if (trackerUrl.contains(macro)) {
                    trackerUrl = StringUtils.replace(trackerUrl, macro, price);
                }
            }
        }
        if (StringUtils.equals(dspChannelId, SaturnDsp.DIDI_CHANNEL_ID) || StringUtils.equals(dspChannelId, SaturnDsp.DIDI_PB_CHANNEL_ID)) {
            for (String macro : ENCRYPTED_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    byte[] initVector = crypto.createInitVector(System.currentTimeMillis(), 1L);
                    String encrytedPrice = crypto.encodePriceValue(Double.valueOf(price), initVector);
                    trackerUrl = StringUtils.replace(trackerUrl, macro, encrytedPrice);
                }
            }
        }

        if (StringUtils.equals(dspChannelId, SaturnDsp.ALI_CHANNEL_ID)) {
            for (String macro : ALI_ENCRYPTED_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ALI_AES_SECRET.getBytes(), "AES"));
                    String settlePrice = new String(Base64.getEncoder().encode(cipher.doFinal(price.getBytes())));
                    trackerUrl = StringUtils.replace(trackerUrl, macro, settlePrice);
                }
            }
        }
        if (StringUtils.equals(dspChannelId, SaturnDsp.YIDIAN_CHANNEL_ID)) {
            for (String macro : YIDIAN_ENCRYPTED_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    String encryptedPrice = YidianPriceEncryptUtils.yidianEncodedPrice(price, winAd.getExt());
                    trackerUrl = StringUtils.replace(trackerUrl, macro, encryptedPrice);
                }
            }
        }
        if (StringUtils.equals(dspChannelId, SaturnDsp.MEITUAN_WAIMAI_CHANNEL_ID)) {
            for (String macro : MTWM_ENCRYPTED_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    String encryptedPrice = mtwmEncrypt(price, "GWNYPGWNTMCDWIOW");
                    trackerUrl = StringUtils.replace(trackerUrl, macro, encryptedPrice);
                }
            }
        }
        if (StringUtils.equals(dspChannelId, SaturnDsp.GDT_CUSTOM_CHANNEL_ID)){
            for (String macro : GDT_CUSTOM_ENCRYPTED_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    String encryptedPrice = encryptByPublicKey(GDT_CUSTOM_RSA, price);
                    trackerUrl = StringUtils.replace(trackerUrl, macro, encryptedPrice);
                }
            }
        }
        if (StringUtils.equals(dspChannelId, SaturnDsp.BXM_PG_RTB_CHANNEL_ID)){
            for (String macro : BXM_PG_RTB_CUSTOM_PRICE_MACROS) {
                if (trackerUrl.contains(macro)) {
                    String encryptedPrice = encryptBxmPrice(BXM_AES_SECRET, price);
                    trackerUrl = StringUtils.replace(trackerUrl, macro, encryptedPrice);
                }
            }
        }
        return trackerUrl;
    }

    private static String mtwmEncrypt(String plaintext, String encryptionKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            // PKCS5Padding比PKCS7Padding效率高，PKCS7Padding可支持IOS加解密
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return BaseEncoding.base64Url().encode(encryptedData);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("encrypt NoSuchAlgorithmException", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("encrypt InvalidKeyException", e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("encrypt NoSuchPaddingException", e);
        } catch (BadPaddingException e) {
            throw new RuntimeException("encrypt BadPaddingException", e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException("encrypt IllegalBlockSizeException", e);
        }
    }

    /**
     * 公钥加密
     * @param base64PublicKey base64加密的公钥
     * @param data 需要加密的字符串
     * @return base64加密的加密byte数组
     * @throws Exception
     */
    private static String encryptByPublicKey(String base64PublicKey, String data){
        try {
            byte[] key = Base64.getDecoder().decode(base64PublicKey);
            //实例化密钥工厂
            String RSA_ALGORITHM = "RSA";
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            //初始化公钥,根据给定的编码密钥创建一个新的 X509EncodedKeySpec。
            X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(key);
            PublicKey publicKey = keyFactory.generatePublic(x509EncodedKeySpec);
            //数据加密
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            log.warn("encryptByPublicKey error {}", e);
        }
        return null;
    }

    private static String encryptBxmPrice(String aesSecret,String price){
        try {
            SecretKeySpec key = new SecretKeySpec(aesSecret.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedData = cipher.doFinal(price.getBytes(StandardCharsets.UTF_8));
            String encode = BaseEncoding.base64().encode(encryptedData);
            return URLEncoder.encode(encode,"UTF-8");
        }catch (UnsupportedEncodingException e){
            throw new RuntimeException("encrypt UnsupportedEncodingException", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("encrypt NoSuchAlgorithmException", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("encrypt InvalidKeyException", e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("encrypt NoSuchPaddingException", e);
        } catch (BadPaddingException e) {
            throw new RuntimeException("encrypt BadPaddingException", e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException("encrypt IllegalBlockSizeException", e);
        }
    }
}