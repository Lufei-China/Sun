package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;


import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.SecureSession;
import lombok.Builder;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.inmobi.supply.sun.constant.RequestType.SDK;

/**
 * @Description NormalizerRequestSDKWorker
 * @Date 2022/5/25 15:54
 * @Author william.wang
 */
@Component
@Builder
public class NormalizerRequestSDKWorker implements NormalizerRequestWorker {

    @Autowired
    @Qualifier("pkMap")
    private Map<String, RSAPrivateKey> pkMap;

    @Override
    public void parseRequest(RunTimeContext runTimeContext) throws SunException {
        String requestBody = new String(runTimeContext.getInputStream());
        if (StringUtils.isBlank(requestBody)) {
            throw new SunException(TermCode.BODY_EMPTY, runTimeContext);
        }
        Map<String, String> sdkParamMap = parseSdkRequest(requestBody, runTimeContext);
        if (MapUtils.isEmpty(sdkParamMap)) {
            throw new SunException(TermCode.BODY_SDK_INVALID, runTimeContext);
        }
        try {
            String publicKeyVersion = sdkParamMap.getOrDefault("sn", "");
            String secureMessage = sdkParamMap.getOrDefault("sm", "");
            if (StringUtils.isNotBlank(publicKeyVersion) && StringUtils.isNotBlank(secureMessage)) {
                String urlDecodedSecureMessage = URLDecoder.decode(secureMessage, "UTF-8");
                SecureSession session = getSession(publicKeyVersion, urlDecodedSecureMessage, runTimeContext);
                runTimeContext.setSecureSession(session);
                runTimeContext.setSecureMessageFlag(true);
                String decryptedRequest = session.getDecodedMessage();
                Map<String, String> decryptedSdkRequest = parseSdkRequest(decryptedRequest, runTimeContext);
                if (MapUtils.isEmpty(decryptedSdkRequest)) {
                    LogUtils.logInfo(runTimeContext.getRequestId(), "NormalizerRequest decryptedSdkRequest empty");
                    throw new SunException(TermCode.BAD_SEC_REQ, runTimeContext);
                }
                runTimeContext.setSdkRequest(decryptedSdkRequest);
            } else {
                runTimeContext.setSdkRequest(sdkParamMap);
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "NormalizerRequest sdkSecureRequestParser error {}", e);
            throw new SunException(TermCode.BAD_SEC_REQ, runTimeContext, e);
        }
    }

    private SecureSession getSession(String version, String message, RunTimeContext runtimeTask) {
        RSAPrivateKey pk = pkMap.get(version);
        runtimeTask.setRsaPrivateKey(pk);
        return new SecureSession(message.getBytes(StandardCharsets.UTF_8), pk);
    }

    private Map<String, String> parseSdkRequest(String requestBody, RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(), "sdk body:{}", requestBody);
        Map<String, String> bodyMap = new HashMap<>();
        try {
            String skdv = "1";
            String skdm = null;
            String[] split = StringUtils.split(requestBody, SymbolString.AND);
            for (String entry : split) {
                String[] kv = StringUtils.split(entry, SymbolString.EQUAL);
                if (kv.length > 1) {
                    String key = URLDecoder.decode(StringUtils.trim(kv[0]), "UTF-8");
                    String value = URLDecoder.decode(StringUtils.trim(kv[1]), "UTF-8");
                    bodyMap.put(key, value);
                    if (key.equals("skdv")) {
                        skdv = value;
                    } else if (key.equals("skdm")) {
                        skdm = value;
                    }
                }
            }
            if (null != skdm) {
                handleSkdvSkdm(skdv, skdm, runTimeContext);
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "NormalizerRequest sdkSecureRequestParser error {}", e);
        }
        return bodyMap;
    }

    private void handleSkdvSkdm(String skdv, String skdm, RunTimeContext runTimeContext) {
        runTimeContext.getGoaRtt().setSkdv(skdv);
        runTimeContext.getGoaRtt().setSkdm(skdm);
        RSAPrivateKey pk = pkMap.get(skdv);
        runTimeContext.getGoaRtt().setPrivateKey(pk);
    }
    @Override
    public List<RequestType> getRequestTypeList() {
        return Collections.singletonList(SDK);
    }
}