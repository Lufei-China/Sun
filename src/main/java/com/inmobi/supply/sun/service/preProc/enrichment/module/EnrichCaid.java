package com.inmobi.supply.sun.service.preProc.enrichment.module;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.api.SourceType;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.framework.common.utils.DebugUtils;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiter;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.sun.config.CaidEnrichConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig;
import com.inmobi.supply.sun.constant.CaidRetCode;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.model.caid.CaidRequestParam;
import com.inmobi.supply.sun.model.caid.CaidResponse;
import com.inmobi.supply.sun.model.caid.CaidResult;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.CaidRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import static com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig.CAID_SUPPORT;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.PUBLISHER_SUPPORT_CAID;

/**
 * @author remi.li
 * <p>
 * caid信息加持
 */
@Slf4j
@Service
@SuppressWarnings("unchecked")
public class EnrichCaid implements ModuleWorker<EnrichmentDataType> {
    public static String CAA_HOST = "https://caid.china-caa.org/v1.0/get";
    public static String CAA_HOST_TEST = "https://caid.china-caa.org/v1.0/get";
    public static String CAA_DEV_ID = "10352";
    public static String PUBK_API = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC2xjXBdff64/0/cmlG3btBfNKXl+k4bTnlHF3WsvIeDvkUlE+2ECCxJe/mrKj6xAX4HzgGOeOe2FjMFxRTzn8vSH8smzz2ZU3WYZoxFxRA6Qzvzp1AeMIx9NAjOmJULP4zoIF+j0m5KKu6yvw/m5mulmg1AMIxxkApMBV19lq59QIDAQAB";
    public static String PUBK = "MIIBkDCB+gIJAJtPS0e45CKbMA0GCSqGSIb3DQEBCwUAMA0xCzAJBgNVBAYTAkNOMB4XDTIxMDIyMzA2MjU0OFoXDTIxMDMyNTA2MjU0OFowDTELMAkGA1UEBhMCQ04wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBALbGNcF19/rj/T9yaUbdu0F80peX6ThtOeUcXday8h4O+RSUT7YQILEl7+asqPrEBfgfOAY5457YWMwXFFPOfy9IfyybPPZlTdZhmjEXFEDpDO/OnUB4wjH00CM6YlQs/jOggX6PSbkoq7rK/D+bma6WaDUAwjHGQCkwFXX2Wrn1AgMBAAEwDQYJKoZIhvcNAQELBQADgYEAXNcgUG9NFnMi3XEmTu9opxdLslCnQff9WulY2uzA6bbBCs7+7kGOxzlqmCLNr7+LOBipaJjlFgFel8nNiuo2Pjwu6Kubt12jOA77EeFObInvncSDmNETXf+etH3cip6AzbAA+k9i85iKyAAcU+7Y8vyPmxHi5T1g4CLmNAOO4Gw=";
    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private CaidRepo caidRepo;

    @Override
    public Mono<EnrichmentDataType> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"EnrichCaid");
        boolean enableGenerateCaid = CAID_SUPPORT.get() && PUBLISHER_SUPPORT_CAID.get().contains(runTimeContext.getPlacementModel().getAccountId());
        EnrichmentDataType enrichmentDataType = new EnrichmentDataType();
        if (runTimeContext.getDevice().getOsType() == OsType.IOS && null != runTimeContext.getDevice().getCaidModel()) {
            Device device = runTimeContext.getDevice();
            CaidModel caidModel = device.getCaidModel();
            String caid = caidModel.getCaid();
            String caidVersion = caidModel.getCaidVersion();
            String preCaid = caidModel.getPreCaid();
            String preCaidVersion = caidModel.getPreCaidVersion();
            if (enableGenerateCaid) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "caidModel={} ", caidModel);
            }
            if (StringUtils.isNotEmpty(caid) && StringUtils.isNotEmpty(caidVersion)) {
                //媒体传了caid过来
                runTimeContext.setCaid(caid);
                runTimeContext.setCaidVersion(caidVersion);
                if (StringUtils.isNotEmpty(preCaid) && StringUtils.isNotEmpty(preCaidVersion)) {
                    runTimeContext.setPreCaid(preCaid);
                    runTimeContext.setPreCaidVersion(preCaidVersion);
                }
                return Mono.just(enrichmentDataType);
            } else if (enableGenerateCaid && !caidModel.isTwelveParamEmpty() && StringUtils.isEmpty(caidModel.getCaid())) {
                //调用caa接口获取caid值
                byte[] pubKeyBytes = Base64.getDecoder().decode(PUBK_API);
                String encryptedDeviceInfo = getEncryptedDeviceInfo(caidModel, pubKeyBytes);
                if (StringUtils.isEmpty(encryptedDeviceInfo)) {
                    return Mono.just(enrichmentDataType);
                }
                //caid http请求参数
                CaidEnrichConfig caidEnrichConfig = DynamicJsonConfig.CAID_ENRICH_CONFIG.get();
                LogUtils.logInfo(runTimeContext.getRequestId(),"caid config {}",ObjectMapperUtils.toJSON(caidEnrichConfig));
                //caid限流参数
                RateLimiterConfig caidRateLimiter = DynamicJsonConfig.CAID_RATE_LIMITER.get();
                Map<String, String> headers = new HashMap<>();
                headers.put("cache-control", "no-cache");
                headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                String requestUrl = (DebugUtils.isDebugEnv()) ? CAA_HOST_TEST : CAA_HOST;
                CaidRequestParam caidRequestParam = new CaidRequestParam(CAA_DEV_ID, encryptedDeviceInfo);
                return (Mono<EnrichmentDataType>) RateLimiter.supplyWithRateLimiter(caidRateLimiter,
                        () -> httpClient
                                .headers(h -> headers.entrySet().iterator().forEachRemaining(header -> h.set(header.getKey(), header.getValue())))
                                .keepAlive(true)
                                .post()
                                .uri(requestUrl)
                                .send(ByteBufFlux.fromString(Mono.just(ObjectMapperUtils.toJSON(caidRequestParam))))
                                .responseContent()
                                .aggregate()
                                .asByteArray()
                                .timeout(Duration.ofMillis(caidEnrichConfig.getTimeout()))
                                .flatMap(res -> {
                                    String resStr = new String(res, Charsets.UTF_8);
                                    if (StringUtils.isNotEmpty(resStr)) {
                                        CaidResult caidResult = ObjectMapperUtils.fromJSON(resStr, CaidResult.class);
                                        Integer code = caidResult.getCode();
                                        String data = caidResult.getData();
                                        try {
                                            if (code.equals(Integer.valueOf(CaidRetCode.SUCCESS.getCode())) && StringUtils.isNotEmpty(data)) {
                                                String caids = getDecryptedCaids(pubKeyBytes, data);
                                                List<CaidResponse> caidResponseList = ObjectMapperUtils.fromJSONArray(caids, CaidResponse.class);
                                                LogUtils.logInfo(runTimeContext.getRequestId(), "caidResponseList  {}", caidResponseList);
                                                if (CollectionUtils.isNotEmpty(caidResponseList)) {
                                                    CaidResponse latestCaidResponse = getLatestCaidResponse(caidResponseList).getLeft();
                                                    CaidResponse olderCaidResponse = getLatestCaidResponse(caidResponseList).getRight();
                                                    runTimeContext.setCaid(latestCaidResponse.getCaid());
                                                    runTimeContext.setCaidVersion(latestCaidResponse.getVersion());
                                                    if (olderCaidResponse != null) {
                                                        runTimeContext.setPreCaid(olderCaidResponse.getCaid());
                                                        runTimeContext.setPreCaidVersion(olderCaidResponse.getVersion());
                                                    }
                                                }
                                            }
                                        } catch (Throwable e) {
                                            LogUtils.logInfo(runTimeContext.getRequestId(), "request error", e);
                                        }
                                        return Mono.just(enrichmentDataType);
                                    }

                                    return Mono.just(enrichmentDataType);
                                }).onErrorResume(throwable -> {
                                    LogUtils.logInfo(runTimeContext.getRequestId(), "caid request error", throwable);
                                    return Mono.just(enrichmentDataType);
                                }).elapsed().flatMap(tuple2 -> {
                                    PerfUtils.timer("caid.latency", tuple2.getT1());
                                    return Mono.just(tuple2.getT2());
                                }), () -> {
                            LogUtils.logInfo(runTimeContext.getRequestId(), "ratalimiter.caid");
                            return Mono.just(enrichmentDataType);
                        });


            }
            //}
            return Mono.just(enrichmentDataType);
        }
        return Mono.just(enrichmentDataType);
    }

    @NonNull
    private String getDecryptedCaids(byte[] pubKeyBytes, String data) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] dataBase64Decoded = Base64.getDecoder().decode(data);
        Cipher decryptedCipher = Cipher.getInstance("RSA");
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(pubKeyBytes));
        decryptedCipher.init(Cipher.DECRYPT_MODE, publicKey);
        int l = dataBase64Decoded.length;
        int offSet = 0;
        int maxDecryptBlock = 128;
        byte[] cache2;
        int i = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (l - offSet > 0) {
            if (l - offSet > maxDecryptBlock) {
                cache2 = decryptedCipher.doFinal(dataBase64Decoded, offSet, maxDecryptBlock);
            } else {
                cache2 = decryptedCipher.doFinal(dataBase64Decoded, offSet, l - offSet);
            }
            out.write(cache2, 0, cache2.length);
            i++;
            offSet = i * maxDecryptBlock;
        }
        String caids = null;
        try {
            caids = out.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("getDecryptedCaids error, data: {} ,e:{}", data, e);
        }
        return caids;
    }

    public String getEncryptedDeviceInfo(CaidModel caidModel, byte[] pubKeyBytes) {
        try {
            HashMap<String, String> deviceInfo = new HashMap();
            deviceInfo.put("bootTimeInSec", caidModel.getBootTimeSecond());
            deviceInfo.put("countryCode", caidModel.getCountry());
            deviceInfo.put("language", caidModel.getLanguage());
            deviceInfo.put("deviceName", caidModel.getPhoneNameMd5());
            deviceInfo.put("systemVersion", caidModel.getOsv());
            deviceInfo.put("machine", caidModel.getDeviceMachine());
            deviceInfo.put("carrierInfo", caidModel.getCarrier());
            deviceInfo.put("memory", String.valueOf(caidModel.getMemorySize().doubleValue()));
            deviceInfo.put("disk", String.valueOf(caidModel.getDiskSize().doubleValue()));
            deviceInfo.put("sysFileTime", caidModel.getOsUpdateTimeSecond());
            deviceInfo.put("model", caidModel.getModelCode());
            deviceInfo.put("timeZone", caidModel.getTimeZone());
            log.info("deviceInfo={}", deviceInfo);

            String jsonStr = new Gson().toJson(new HashMap<>(deviceInfo));
            Cipher encryptCipher = Cipher.getInstance("RSA");
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] b = jsonStr.getBytes("UTF-8");
            System.out.println(b.length);
            int inputLen = b.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offSet = 0;
            int MAX_ENCRYPT_BLOCK = 117;
            byte[] cache;
            int i = 0;
            // 对数据分段加密
            while (inputLen - offSet > 0) {
                if (inputLen - offSet > MAX_ENCRYPT_BLOCK) {
                    cache = encryptCipher.doFinal(b, offSet, MAX_ENCRYPT_BLOCK);
                } else {
                    cache = encryptCipher.doFinal(b, offSet, inputLen - offSet);
                }
                out.write(cache, 0, cache.length);
                i++;
                offSet = i * MAX_ENCRYPT_BLOCK;
            }
            byte[] encryptedBytes = out.toByteArray();
            out.close();
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Throwable e) {
            log.error("getEncryptedDeviceInfo error , caidModel :{} ,e {}", caidModel, e);
            return "";
        }
    }

    /**
     * 获取最新的版本的caid
     * <latestCaid, oldCaid>
     *
     * @param caidResponseList
     * @return
     */
    private Pair<CaidResponse, CaidResponse> getLatestCaidResponse(List<CaidResponse> caidResponseList) {
        if (caidResponseList.size() == 1) {
            return Pair.of(caidResponseList.get(0), null);
        }
        if (caidResponseList.size() == 2) {
            CaidResponse latestCaid = (caidResponseList.get(0).getVersion().compareTo(caidResponseList.get(1).getVersion()) > 0) ? caidResponseList.get(0) : caidResponseList.get(1);
            CaidResponse oldCaid = (caidResponseList.get(0).getVersion().compareTo(caidResponseList.get(1).getVersion()) > 0) ? caidResponseList.get(1) : caidResponseList.get(0);
            return Pair.of(latestCaid, oldCaid);
        }
        return null;
    }
}