package com.inmobi.supply.sun.config.sdkSecure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.DataInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * @author remi.li
 */
@Slf4j
@Configuration
public class UidPrivateKey {
    @Bean
    public RSAPrivateKey uidRSAKey() {
        RSAPrivateKey privateKey = null;
        try {
            InputStream in = getClass().getResourceAsStream("/encrypt/uidMapPrivateKey.der");
            DataInputStream dis = new DataInputStream(in);
            byte[] keyBytes = new byte[dis.available()];
            dis.readFully(keyBytes);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (Exception e) {
            log.error("UidPrivateKey load error", e);
        }
        return privateKey;
    }
}