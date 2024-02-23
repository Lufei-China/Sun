package com.inmobi.supply.sun.config.sdkSecure;

import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author remi.li
 */
@Slf4j
@Configuration
@SuppressWarnings("unchecked")
public class PrivateKey {
    @Bean
    Map<String, RSAPrivateKey> pkMap() {
        Map<String, RSAPrivateKey> pkMap = new HashMap<>();
        InputStream in = getClass().getResourceAsStream("/encrypt/keystore");
        try (BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(in)))) {
            String line;
            while ((line = br.readLine()) != null) {
                List<Map> pkArray = ObjectMapperUtils.fromJSONArray(line, Map.class);
                if (pkArray != null) {
                    for (Object aPkArray : pkArray) {
                        Map pkEntry = (Map) aPkArray;
                        Map entryVal = (Map) pkEntry.getOrDefault("pk", new HashMap<>());
                        String keyVersion = entryVal.getOrDefault("ver", "").toString();
                        Map modExp = (Map) entryVal.getOrDefault("val", new HashMap<>());
                        String modulus = modExp.getOrDefault("m", "").toString();
                        String exponent = modExp.getOrDefault("e", "").toString();
                        RSAPrivateKeySpec privateSpec = new RSAPrivateKeySpec(new BigInteger(modulus, 16),
                                new BigInteger(exponent, 16));
                        KeyFactory kFactory = KeyFactory.getInstance("RSA");
                        RSAPrivateKey privateKey = (RSAPrivateKey) kFactory.generatePrivate(privateSpec);
                        pkMap.put(keyVersion, privateKey);
                    }
                }
            }
        } catch (Exception e) {
            log.error("PrivateKey load pkMap error", e);
        }
        return pkMap;
    }
}