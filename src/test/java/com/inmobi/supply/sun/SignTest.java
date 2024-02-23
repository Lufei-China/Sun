package com.inmobi.supply.sun;

import com.google.common.base.Charsets;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

/**
 * @author zeki.zhao
 */
public class SignTest {
    public static final String sep = "\u2063";

    public static void main(String[] args) {
        generateSign();

//        checkApplePostback();

    }

    private static void checkApplePostback() {
        String version = "2.0";
        String adNetworkId = "com.example";
        int campaignId = 42;
        int appId = 525463029;
        String transactionId = "6aafb7a5-0170-41b5-bbe4-fe71dedf1e28";
        int redownload = 1;
        int sourceAppId = 1234567891;

        String val = new StringBuffer()
                .append(version)
                .append(sep)
                .append(adNetworkId)
                .append(sep)
                .append(campaignId)
                .append(sep)
                .append(appId)
                .append(sep)
                .append(transactionId)
                .append(sep)
                .append(redownload)
                .append(sep)
                .append(sourceAppId)
                .toString();

        String sign = "MDYCGQCsQ4y8d4BlYU9b8Qb9BPWPi+ixk\\/OiRysCGQDZZ8fpJnuqs9my8iSQVbJO\\/oU1AXUROYU=";

        try {
            byte[] encoded = org.apache.commons.codec.binary.Base64.decodeBase64("MEkwEwYHKoZIzj0CAQYIKoZIzj0DAQEDMgAEMyHD625uvsmGq4C43cQ9BnfN2xslVT5V1nOmAMP6qaRRUll3PB1JYmgSm+62sosG");
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(encoded));

            Signature deSign = Signature.getInstance("SHA256withECDSA");
            deSign.initVerify(publicKey);
            deSign.update(val.getBytes(Charsets.UTF_8));
            boolean verify = deSign.verify(org.apache.commons.codec.binary.Base64.decodeBase64(sign.getBytes(Charsets.UTF_8)));
            System.out.println(verify);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    private static void generateSign() {
        try {
            String version = "2.0";
            String adNetworkId = "s4f48nyfvz.skadnetwork";
            String campaignId = "27";
            String itunesItemId = "1510183643";
//            String nonce = SfIdGen.nextId() + String.format("%04d", new Random().nextInt(10000));
            String nonce = UUID.randomUUID().toString().toLowerCase();
            String sourceAppId = "368677368";
            long timeMillis = System.currentTimeMillis();

            System.out.println("nonce: " + nonce);
            System.out.println("timeMillis: " + timeMillis);
//            System.out.println("version: " + version);
            System.out.println("adNetworkId: " + adNetworkId);
            System.out.println("campaignId: " + campaignId);
            System.out.println("itunesItemId: " + itunesItemId);
//            System.out.println("sourceAppId: " + sourceAppId);


            String val = new StringBuffer()
//                    .append(sep)
                    .append(adNetworkId)
                    .append(sep)
                    .append(campaignId)
                    .append(sep)
                    .append(itunesItemId)
                    .append(sep)
                    .append(nonce)
                    .append(sep)
//                    .append(sourceAppId)
//                    .append(sep)
                    .append(timeMillis)
                    .toString();

            System.out.println("sign msg: " + val);
            String enRes = sign(val);
            System.out.println(enRes);

            generateBinFile(val.getBytes(), "message.bin");
            generateBinFile(org.apache.commons.codec.binary.Base64.decodeBase64(enRes), "signature.bin");
//            generateBinFile(Base64.getDecoder().decode(enRes), "signature.bin");

            System.out.println(verify(val, enRes));

        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    private static String sign(String val) throws GeneralSecurityException, IOException {
        Signature enSign = Signature.getInstance("SHA256withECDSA");
//            enSign.initSign(getPrivateKey(SignTest.class.getClassLoader().getResourceAsStream("pk/inmobi-ios-pk.pem")));
        enSign.initSign(getPrivateKey("/Users/zeki.zhao/file/temp/file.pem"));
        enSign.update(val.getBytes());
        byte[] enSignByte = enSign.sign();
//        String enRes = Base64.getEncoder().encodeToString(enSignByte);
        String enRes = org.apache.commons.codec.binary.Base64.encodeBase64String(enSignByte);
        return enRes;
    }

    private static boolean verify(String msg, String sign) throws GeneralSecurityException, IOException {
        Signature deSign = Signature.getInstance("SHA256withECDSA");
        deSign.initVerify(getPublicKey("/Users/zeki.zhao/file/temp/inmobi_skadnetwork_public_key.pkcs8"));
        deSign.update(msg.getBytes());
//        boolean verify = deSign.verify(Base64.getDecoder().decode(sign));
        boolean verify = deSign.verify(org.apache.commons.codec.binary.Base64.decodeBase64(sign));
        return verify;
    }

    public static void generateBinFile(byte[] val, String fileName) throws IOException {
        DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(fileName)));
        os.write(val);
        os.flush();
        os.close();
    }

    public static String getKey(String filename) throws IOException {
        // Read key from file
        String strKeyPEM = "";
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = br.readLine()) != null) {
            strKeyPEM += line + "\n";
        }
        br.close();
        return strKeyPEM;
    }

    public static PublicKey getPublicKey(String filename) throws IOException, GeneralSecurityException {
        String publicKeyPEM = getKey(filename);
        return getPublicKeyFromString(publicKeyPEM);
    }

    public static PublicKey getPublicKeyFromString(String key) throws IOException, GeneralSecurityException {
        String publicKeyPEM = key;
        publicKeyPEM = publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----\n", "");
        publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");
        publicKeyPEM = publicKeyPEM.replaceAll("\n", "");
        publicKeyPEM = publicKeyPEM.replaceAll(" ", "");
        byte[] encoded = org.apache.commons.codec.binary.Base64.decodeBase64(publicKeyPEM);
//        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(encoded));
        return publicKey;
    }

    public static PrivateKey getPrivateKey(String filename) throws IOException, GeneralSecurityException {
        String privateKeyPEM = getKey(filename);
        return getPrivateKeyFromString(privateKeyPEM);
    }

    public static PrivateKey getPrivateKey(InputStream inputStream) throws IOException, GeneralSecurityException {
        String privateKeyPEM = getKey(inputStream);
        return getPrivateKeyFromString(privateKeyPEM);
    }

    public static String getKey(InputStream inputStream) throws IOException {
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

    public static PrivateKey getPrivateKeyFromString(String key) throws IOException, GeneralSecurityException {
        String privateKeyPEM = key;
        privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
        privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
        privateKeyPEM = privateKeyPEM.replaceAll("\n", "");
        privateKeyPEM = privateKeyPEM.replaceAll(" ", "");
        byte[] encoded = org.apache.commons.codec.binary.Base64.decodeBase64(privateKeyPEM);
//        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        return privateKey;
    }
}
