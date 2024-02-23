package com.inmobi.supply.sun.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.inmobi.supply.component.model.rr.ApiV33Response;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zeki.zhao
 */
@Slf4j
public class GenerateSignUtil {

    public static final String SEP = "\u2063";

    public static String generateSign(ApiV33Response.Ads.AdExt.Skad skad, String version) {
        try {
            String val = "";
            switch (version) {
                case "1.0":
                    val = new StringBuffer(skad.getNetwork())
                            .append(SEP)
                            .append(skad.getCampaign())
                            .append(SEP)
                            .append(skad.getItunesitem())
                            .append(SEP)
                            .append(skad.getNonce())
                            .append(SEP)
                            .append(skad.getTimestamp())
                            .toString();
                    break;
                case "2.0":
                    val = new StringBuffer(skad.getVersion())
                            .append(SEP)
                            .append(skad.getNetwork())
                            .append(SEP)
                            .append(skad.getCampaign())
                            .append(SEP)
                            .append(skad.getItunesitem())
                            .append(SEP)
                            .append(skad.getNonce())
                            .append(SEP)
                            .append(skad.getSourceapp())
                            .append(SEP)
                            .append(skad.getTimestamp())
                            .toString();
                    break;
                case "2.2":
                    val = new StringBuffer(skad.getVersion())
                            .append(SEP)
                            .append(skad.getNetwork())
                            .append(SEP)
                            .append(skad.getCampaign())
                            .append(SEP)
                            .append(skad.getItunesitem())
                            .append(SEP)
                            .append(skad.getNonce())
                            .append(SEP)
                            .append(skad.getSourceapp())
                            .append(SEP)
                            .append(skad.getFidelityType())
                            .append(SEP)
                            .append(skad.getTimestamp())
                            .toString();
                    break;
            }

            Signature enSign = Signature.getInstance("SHA256withECDSA");
            enSign.initSign(getPrivateKey(GenerateSignUtil.class.getClassLoader().getResourceAsStream("pk/inmobi-ios-pk.pem")));
            enSign.update(val.getBytes());
            byte[] enSignByte = enSign.sign();
            String enRes = Base64.getEncoder().encodeToString(enSignByte);
            //log.debug("GenerateSignUtil generateSign: message: {}, sign: {}", val, enRes);
            return enRes;
        } catch (IOException | GeneralSecurityException e) {
            log.error("generate sign error", e);
        }
        return null;
    }

    private static PrivateKey getPrivateKey(InputStream inputStream) throws IOException, GeneralSecurityException {
        String privateKeyPEM = getKey(inputStream);
        return getPrivateKeyFromString(privateKeyPEM);
    }

    private static String getKey(InputStream inputStream) throws IOException {
        // Read key from file
        String strKeyPEM = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = br.readLine()) != null) {
            strKeyPEM += line + "\n";
        }
        br.close();
        return strKeyPEM;
    }

    private static PrivateKey getPrivateKeyFromString(String key) throws GeneralSecurityException {
        String privateKeyPEM = key;
        privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
        privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
        privateKeyPEM = privateKeyPEM.replaceAll("\n", "");
        privateKeyPEM = privateKeyPEM.replaceAll(" ", "");
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        return privateKey;
    }
}
