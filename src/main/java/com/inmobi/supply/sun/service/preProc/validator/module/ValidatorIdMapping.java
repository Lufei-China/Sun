package com.inmobi.supply.sun.service.preProc.validator.module;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.api.Version;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.utils.Base64;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.ApiOrtbContext;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.HmacMd5CrcUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.conscrypt.OpenSSLMac;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.SdkParam.U_ID_ADT;
import static com.inmobi.supply.sun.constant.SdkParam.U_TRACKING_STATUS;
import static com.inmobi.supply.sun.constant.UidType.*;


/**
 * Gavin.xu
 * 2020.5.18
 * 手机型号获取和UA校验
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class ValidatorIdMapping implements ModuleWorker<Integer> {
    @Autowired
    @Qualifier("uidRSAKey")
    private RSAPrivateKey uidRSAPrivateKey;

    private static final Pattern nonAlphaNumeric = Pattern.compile("[^\\w\\-]+");
    private static final Pattern validIDFA = Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-[1-5][0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}");
    private static final Pattern validIDFV = Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
    private static final String ZERO = "00000000-0000-0000-0000-000000000000";
    private static final Pattern validOAID = Pattern.compile("[0-9a-zA-Z_-]{16,64}");
    private static final Pattern validO1 = Pattern.compile("[0-9a-fA-F]{40}");
    private static final int MD5_HASH_LENGTH = 32;
    private static final int SHA1_HASH_LENGTH = 40;
    private static final int IMEI_LENGTH = 15;
    private static final int UM5_LENGTH = 32;

    //非法imei md5集合
    private static final List<String> INVALID_IMEI_MD5 = Arrays.asList("d41d8cd98f00b204e9800998ecf8427e","ad921d60486366258809553a3db49a4a",
            "37a6259cc0c1dae299a7866489dff0bd","cfcd208495d565ef66e7dff9f98764da",
            "851dad2e57800376ccfb18ab71b95c80","670b14728ad9902aecba32e22fa4f6bd",
            "dd4b21e9ef71e1291183a46b913ae6f2","e10adc3949ba59abbe56e057f20f883e",
            "96e79218965eb72c92a549dd5a330112","4ba36d23a78c7393b4900ef38019d8ff",
            "696b031073e74bf2cb98e5ef201d4aa3","6c3e226b4d4795d518ab341b0824ec29", "6666cd76f96956469e7be39d750cc7d9");

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"ValidatorIdMapping");
            OsType osType = runTimeContext.getDevice().getOsType();
            Version.Builder versionBuilder = Version.newBuilder();
            if (osType == OsType.IOS) {
                PerfUtils.perf("ios.request");
                if (StringUtils.isNotBlank(runTimeContext.getDevice().getOsVersion())) {
                    String[] versionSegments = StringUtils.split(runTimeContext.getDevice().getOsVersion(), ".");
                    try {
                        if (versionSegments.length >= 3) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                            versionBuilder.setMinor(Optional.ofNullable(Integer.valueOf(versionSegments[1])).orElse(0));
                            versionBuilder.setMicro(Optional.ofNullable(Integer.valueOf(versionSegments[2])).orElse(0));
                        } else if (versionSegments.length == 2) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                            versionBuilder.setMinor(Optional.ofNullable(Integer.valueOf(versionSegments[1])).orElse(0));
                        } else if (versionSegments.length == 1) {
                            versionBuilder.setMajor(Optional.ofNullable(Integer.valueOf(versionSegments[0])).orElse(0));
                        }
                    } catch (Exception e) {
                        //do nothing
                    }
                }
            }

            if (runTimeContext.getRequestType().isSdk()) {
                validateSdk(runTimeContext, versionBuilder.build());
            } else if (runTimeContext.getRequestType().equals(RequestType.API_ORTB_1_0)) {
                validateApiOrtb(runTimeContext);
            } else {
                validateApi(runTimeContext);
            }
            //判断是否为测试流量
            runTimeContext.setTestTraffic(false);
            if (CollectionUtils.isNotEmpty(runTimeContext.getPlacementModel().getTestDeviceList())
                    && runTimeContext.getDevice() != null) {
                Device.IdMapping idMapping = runTimeContext.getDevice().getId();

                //1:idfa 2:imei_md5 3:imei_sha1 4:androidId_md5 5:androidId_sha1 6:oaid
                Map<Integer, String> userDeviceIdMap = new HashMap<>();
                if (StringUtils.isNotBlank(idMapping.getIdfa())) {
                    userDeviceIdMap.put(1, idMapping.getIdfa());
                }
                if (StringUtils.isNotBlank(idMapping.getImeiMd5())) {
                    userDeviceIdMap.put(2, idMapping.getImeiMd5());
                }
                if (StringUtils.isNotBlank(idMapping.getImeiSha1())) {
                    userDeviceIdMap.put(3, idMapping.getImeiSha1());
                }
                if (StringUtils.isNotBlank(idMapping.getUm5())) {
                    userDeviceIdMap.put(4, idMapping.getUm5());
                }
                if (StringUtils.isNotBlank(idMapping.getO1())) {
                    userDeviceIdMap.put(5, idMapping.getO1());
                }
                if (StringUtils.isNotBlank(idMapping.getOaid())) {
                    userDeviceIdMap.put(6, idMapping.getOaid());
                }
                runTimeContext.getPlacementModel()
                        .getTestDeviceList().stream()
                        .filter(testDevice -> StringUtils.isNotBlank(testDevice.getDeviceId()))
                        .forEach(testDevice -> {
                            if (userDeviceIdMap.containsKey(testDevice.getDeviceIdType())
                                    && StringUtils.isNotBlank(userDeviceIdMap.getOrDefault(testDevice.getDeviceIdType(), ""))
                                    && userDeviceIdMap.get(testDevice.getDeviceIdType()).equals(testDevice.getDeviceId())) {
                                runTimeContext.getTestDeviceList().add(testDevice);
                            }
                        });
                if (CollectionUtils.isNotEmpty(runTimeContext.getTestDeviceList())) {
                    runTimeContext.setTestTraffic(true);
                }
            }
            if (runTimeContext.getPlacementModel().getIsTestPlacement() == 1) {
                //测试广告位
                runTimeContext.setTestTraffic(true);
            }
            if (SymbolString.TRUE.equalsIgnoreCase(runTimeContext.getHeaders().get(StringUtils.lowerCase(RequestHeader.MOCK_AD_HEADER)))) {
                runTimeContext.setTestTraffic(true);
            }
            if (CollectionUtils.isNotEmpty(runTimeContext.getTermCodes())) {
                if (runTimeContext.getTermCodes().size() == 1
                        && runTimeContext.getTermCodes().get(0).equals(TermCode.APP_INVALID)
                        && runTimeContext.getTestTraffic()) {
                    //测试流量不抛APP_INVALID
                    return Mono.just(1);
                }
                //validatorDevice + ValidatorIdMapping两个类的错误集中在这里抛，为了尽量留住用户的数据到sunRR中
                throw new SunException(runTimeContext.getTermCodes().get(0), runTimeContext);
            }
            return Mono.just(1);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(),this.getClass().getSimpleName()+ " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(),this.getClass().getSimpleName()+ " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void validateApi(RunTimeContext runTimeContext) {
        Device.IdMapping.IdMappingBuilder builder = Device.IdMapping.builder();
        OsType osType = runTimeContext.getDevice().getOsType();
        cn.inmobi.pb.sun.rr.base.Device device = runTimeContext.getBaseRequest().getDevice();
        String imeiMd5 = Optional.ofNullable(device.getMd5Imei()).orElse("");
        String oaid = Optional.ofNullable(device.getOaid()).orElse("");
        String imei = Optional.ofNullable(device.getIem()).orElse("");
        String imeiSha1 = Optional.ofNullable(device.getSha1Imei()).orElse("");
        String androidMd5 = Optional.ofNullable(device.getUm5()).orElse("");
        String androidSha1 = Optional.ofNullable(device.getO1()).orElse("");
        String idfa = Optional.ofNullable(device.getIfa()).orElse("");
        String idfv = Optional.ofNullable(device.getIdfv()).orElse("");
        String idfaMd5 = Optional.ofNullable(device.getIdfaMd5()).orElse("");
        String oaidMd5 = Optional.ofNullable(device.getOaidMd5()).orElse("");
        String paid = Optional.ofNullable(device.getPaid()).orElse("");
        String mac = Optional.ofNullable(device.getMac()).orElse("00:00:00:00:00:00");
        String macMd5 = Optional.ofNullable(device.getMacMd5()).orElse(DigestUtils.md5Hex("00:00:00:00:00:00"));
        String androidId = Optional.ofNullable(device.getAndroidId()).orElse("");
        if(StringUtils.isNotBlank(androidId)){
            androidId = StringUtils.lowerCase(androidId);
        }

        boolean androidDeviceIdExist = true;
        boolean iosDeviceIdExist = true;
        boolean isTwelveParamNotEmpty = false;

                CaidModel caidModel = runTimeContext.getDevice().getCaidModel();
        if (!Objects.isNull(caidModel)) {
            String osUpdateTimeSecond = caidModel.getOsUpdateTimeSecond();
            boolean checkOsUpdateTimeSecond = false;
            if (StringUtils.isNotEmpty(osUpdateTimeSecond)) {
                String[] split = StringUtils.split(osUpdateTimeSecond, ".");
                if (split.length == 2 && split[1].length() == 6) {
                    checkOsUpdateTimeSecond = true;
                }
            }
            isTwelveParamNotEmpty = StringUtils.isNotEmpty(caidModel.getBootTimeSecond())
                    && StringUtils.isNotEmpty(caidModel.getLanguage()) && StringUtils.isNotEmpty(caidModel.getPhoneNameMd5())
                    && StringUtils.isNotEmpty(caidModel.getOsv()) && StringUtils.isNotEmpty(caidModel.getDeviceMachine())
                    && StringUtils.isNotEmpty(caidModel.getCarrier()) && caidModel.getMemorySize() != null
                    && caidModel.getDiskSize() != null && checkOsUpdateTimeSecond
                    && StringUtils.isNotEmpty(caidModel.getModelCode()) && StringUtils.isNotEmpty(caidModel.getTimeZone());
            log.info("reqid: {}, caidModel:{} , isTwelveParamNotEmpty: {}", runTimeContext.getRequestId(), caidModel, isTwelveParamNotEmpty);
            caidModel.setTwelveParamEmpty(!isTwelveParamNotEmpty);
        }
        if (runTimeContext.getRequestType() == RequestType.API_3_4_1 && osType == OsType.IOS) {
            //对于api3.4.1需要检查ios14参数
            if (!isTwelveParamNotEmpty && StringUtils.isEmpty(caidModel.getCaid()) && StringUtils.isBlank(idfa)) {
                //十二项参数、caid、idfa均为空
                iosDeviceIdExist = false;
            } else if (StringUtils.isNotEmpty(caidModel.getCaid()) && StringUtils.isEmpty(caidModel.getCaidVersion())) {
                //caid与caidVersion必须同时传递才可以
                iosDeviceIdExist = false;
            }
        } else {
            if (StringUtils.isBlank(idfa) && StringUtils.isBlank(idfaMd5)) {
                iosDeviceIdExist = false;
            }
        }

        if (StringUtils.isBlank(imeiMd5)
                && StringUtils.isBlank(oaid)
                && StringUtils.isBlank(imei)
                && StringUtils.isBlank(imeiSha1)
                && StringUtils.isBlank(androidSha1)
                && StringUtils.isBlank(androidMd5)
                && StringUtils.isBlank(oaidMd5)) {
            //android设备id全部是空
            androidDeviceIdExist = false;
        }

        if (StringUtils.isBlank(idfa) && StringUtils.isBlank(idfaMd5)) {
            iosDeviceIdExist = false;
        }

        if (osType == OsType.ANDROID) {
            builder.oaid(oaid)
                    .o1(androidSha1)
                    .imeiMd5(imeiMd5)
                    .imei(imei)
                    .imeiSha1(imeiSha1)
                    .um5(androidMd5)
                    .oaidMd5(oaidMd5);
            if (!androidDeviceIdExist) {
                runTimeContext.getTermCodes().add(TermCode.ANDROID_ID_EMPTY);
            }
        }

        boolean needCheckIdfa = true;
        if (osType == OsType.IOS) {
            builder.idfa(idfa);
            builder.idfv(idfv);
            builder.idfaMd5(idfaMd5);
            builder.paid(paid);
            PerfUtils.perf("api.lmt", "value", String.valueOf(device.getLmt()));
            runTimeContext.setLat(device.getLmt());
            if (device.getLmt() != 3 && StringUtils.isNotEmpty(idfa) && idfa.equals(ZERO)) {
                //没有授权idfa,并且idfa等于全0
                log.info("lat request={} {}", runTimeContext.getBaseRequest(), runTimeContext.getRequestId());
                needCheckIdfa = false;
            } else {
                //用户授权lmt,继续检查idfa
                if (!iosDeviceIdExist) {
                    runTimeContext.getTermCodes().add(TermCode.IOS_ID_EMPTY);
                }
            }
        }

        if (androidDeviceIdExist && iosDeviceIdExist) {
            //都存在，报错
            runTimeContext.getTermCodes().add(TermCode.ANDROID_IOS_ID_BOTH_EXIST);
        }

        builder.mac(mac).macMd5(macMd5).androidId(androidId);
        Device.IdMapping idMapping = builder.build();
        runTimeContext.getDevice().setId(idMapping);
        checkIds(runTimeContext, idMapping, needCheckIdfa);
    }

    private boolean validateCaidModelForIOS(RunTimeContext runTimeContext, String idfa,
                                            boolean iosDeviceIdExist) {
        CaidModel caidModel = runTimeContext.getDevice().getCaidModel();

        String osUpdateTimeSecond = caidModel.getOsUpdateTimeSecond();
        boolean checkOsUpdateTimeSecond = false;
        if (StringUtils.isNotEmpty(osUpdateTimeSecond)) {
            String[] split = StringUtils.split(osUpdateTimeSecond,".");
            if (split.length == 2 && split[1].length() == 6) {
                checkOsUpdateTimeSecond = true;
            }
        }
        boolean isTwelveParamNotEmpty = StringUtils.isNotEmpty(caidModel.getBootTimeSecond())
                && StringUtils.isNotEmpty(caidModel.getLanguage()) && StringUtils.isNotEmpty(caidModel.getPhoneNameMd5())
                && StringUtils.isNotEmpty(caidModel.getOsv()) && StringUtils.isNotEmpty(caidModel.getDeviceMachine())
                && StringUtils.isNotEmpty(caidModel.getCarrier()) && caidModel.getMemorySize() != null
                && caidModel.getDiskSize() != null && checkOsUpdateTimeSecond
                && StringUtils.isNotEmpty(caidModel.getModelCode()) && StringUtils.isNotEmpty(caidModel.getTimeZone());

        caidModel.setTwelveParamEmpty(!isTwelveParamNotEmpty);
        if (!isTwelveParamNotEmpty && StringUtils.isEmpty(caidModel.getCaid()) && StringUtils.isBlank(
                idfa)) {
            //十二项参数、caid、idfa均为空
            iosDeviceIdExist = false;
        } else if (StringUtils.isNotEmpty(caidModel.getCaid()) && StringUtils.isEmpty(caidModel.getCaidVersion())) {
            //caid与caidVersion必须同时传递才可以
            iosDeviceIdExist = false;
        }
        return iosDeviceIdExist;
    }

    private void validateApiOrtb(RunTimeContext runTimeContext) {
        Device.IdMapping.IdMappingBuilder builder = Device.IdMapping.builder();
        OsType osType = runTimeContext.getDevice().getOsType();
        ApiOrtbContext apiContext = null;
//                (ApiOrtbContext) runTimeContext.getApiv3Request();
        cn.inmobi.pb.sun.rr.api.ortb.Device device = apiContext.getApiOrtbRequest().getDevice();

        boolean androidDeviceIdExist = true;
        boolean iosDeviceIdExist = true;
        boolean needCheckIdfa = true;

        if (osType == OsType.IOS) {
            String idfa = Optional.of(device.getIfa()).orElse("");
            String idfv = Optional.of(Optional.of(device.getExt().getIdv()).orElse(""))
                    .orElse("");
            builder.idfa(idfa);
            builder.idfv(idfv);

            if (idfa.isEmpty()) {
                iosDeviceIdExist = false;
            }

            if (device.getLmt() != 3 && StringUtils.isNotEmpty(idfa) && idfa.equals(ZERO)) {
                //没有授权idfa,并且idfa等于全0
                log.info("lat request={} {}", runTimeContext.getBaseRequest(), runTimeContext.getRequestId());
                needCheckIdfa = false;
            } else {
                //用户授权lmt,继续检查idfa
                if (!iosDeviceIdExist) {
                    runTimeContext.getTermCodes().add(TermCode.IOS_ID_EMPTY);
                }
            }
            PerfUtils.perf("api.lmt", "value", String.valueOf(device.getLmt()));
            runTimeContext.setLat(device.getLmt());
        }

        if (osType == OsType.ANDROID) {
            String imei = Optional.of(device.getIfa()).orElse("");
            if (imei.isEmpty()) {
                androidDeviceIdExist = false;
            }
            builder.imei(imei).build();
            if (!androidDeviceIdExist) {
                runTimeContext.getTermCodes().add(TermCode.ANDROID_ID_EMPTY);
            }
        }

        Device.IdMapping idMapping = builder.build();
        runTimeContext.getDevice().setId(idMapping);
        checkIds(runTimeContext, idMapping, needCheckIdfa);
    }
    private void validateSdk(RunTimeContext runTimeContext, Version iosVersion) {
        Device.IdMapping.IdMappingBuilder builder = Device.IdMapping.builder();
        OsType osType = runTimeContext.getDevice().getOsType();
        Map<String, String> uidMap = parseUidMap(runTimeContext, osType);
        String[] md5ImeiList = StringUtils.split(uidMap.getOrDefault(MD5_IMEI.getValue(), ""), SymbolString.COMMA);
        String[] sha1ImeiList = StringUtils.split(uidMap.getOrDefault(SHA1_IMEI.getValue(), ""), SymbolString.COMMA);

        String md5Imei = "";
        String sha1Imei = "";
        String oaid = "";
        String o1 = uidMap.getOrDefault(ANDROID_ID_SHA1.getValue(), "");
        String um5 = uidMap.getOrDefault(ANDROID_ID_MD5.getValue(), "");
        if (ArrayUtils.isNotEmpty(md5ImeiList)) {
            //deviceId = md5ImeiList[0];
            md5Imei = Optional.ofNullable(md5ImeiList[0]).orElse("");
        }
        if (ArrayUtils.isNotEmpty(sha1ImeiList)) {
            sha1Imei = Optional.ofNullable(sha1ImeiList[0]).orElse("");
        }
        if (StringUtils.isNotBlank(uidMap.get(OAID.getValue()))) {
            oaid = uidMap.get(OAID.getValue());
        }

        String idfa = "";
        if (StringUtils.isNotBlank(uidMap.get(IDFA.getValue()))) {
            idfa = uidMap.get(IDFA.getValue());
        }
        boolean androidDeviceIdExist = true;
        boolean iosDeviceIdExist = true;

        if (StringUtils.isBlank(md5Imei)
                && StringUtils.isBlank(oaid)
                && StringUtils.isBlank(o1)
                && StringUtils.isBlank(um5)
                && StringUtils.isBlank(sha1Imei)) {
            androidDeviceIdExist = false;
        }

        if (StringUtils.isBlank(idfa)) {
            iosDeviceIdExist = false;
        }

        if (osType == OsType.ANDROID) {
            builder.imeiMd5(md5Imei)
                    .imeiSha1(sha1Imei)
                    .oaid(oaid)
                    .o1(o1)
                    .um5(um5);
            if (!androidDeviceIdExist) {
                runTimeContext.getTermCodes().add(TermCode.ANDROID_ID_EMPTY);
            }
        }
        boolean needCheckIdfa = true;
        if (osType == OsType.IOS) {
            builder.idfa(idfa);
            if (iosVersion.getMajor() >= 14) {
                String trackingStatus = runTimeContext.getSdkRequest().getOrDefault(U_TRACKING_STATUS.getParam(), "0");
                PerfUtils.perf("sdk.trackingStatus", "value", trackingStatus);
                try {
                    runTimeContext.setLat((Integer.parseInt(trackingStatus)));
                } catch (Throwable e) {
                    log.error("trackingStatus parse int error", e);
                    runTimeContext.setLat(LatType.NOT_DETERMINED.getValue());
                }
                if (!trackingStatus.equals("3") && StringUtils.isNotEmpty(idfa) && idfa.equals(ZERO)) {
                    //没有授权idfa,并且idfa等于全0，不检查idfa了
                    needCheckIdfa = false;
                } else {
                    //1.用户授权IDFA传递,正常进行idfa检查
                    if (!iosDeviceIdExist) {
                        runTimeContext.getTermCodes().add(TermCode.IOS_ID_EMPTY);
                    }
                }
            } else {
                String uIdAdt = runTimeContext.getSdkRequest().getOrDefault(U_ID_ADT.getParam(), "0");
                PerfUtils.perf("sdk.uIdAdt", "value", uIdAdt);
                if (uIdAdt.equals("0")) {
                    runTimeContext.setLat(LatType.AUTHORIZED.getValue());
                } else {
                    runTimeContext.setLat(LatType.NOT_DETERMINED.getValue());
                }
                if (!uIdAdt.equals("0") && StringUtils.isNotEmpty(idfa) && idfa.equals(ZERO)) {
                    //不授权，不用开启idfa检查了
                    needCheckIdfa = false;
                } else {
                    //1.用户授权IDFA传递,正常进行idfa检查
                    /*if (StringUtils.isBlank(idfa)) {
                        iosDeviceIdExist = false;
                    }*/
                    builder.idfa(idfa);
                    //2.check
                    if (!iosDeviceIdExist) {
                        runTimeContext.getTermCodes().add(TermCode.IOS_ID_EMPTY);
                    }
                }
            }
        }

        if (androidDeviceIdExist && iosDeviceIdExist) {
            //都存在，报错
            runTimeContext.getTermCodes().add(TermCode.ANDROID_IOS_ID_BOTH_EXIST);
        }

        Device.IdMapping idMapping = builder.build();
        runTimeContext.getDevice().setId(idMapping);
        checkIds(runTimeContext, idMapping, needCheckIdfa);
    }


    /**
     * 解析sdk的uidmap
     *
     * @param runTimeContext
     * @param osType
     * @return
     * @throws SunException
     */
    private Map<String, String> parseUidMap(RunTimeContext runTimeContext, OsType osType) {
        Map<String, String> uidMap = new HashMap<>();
        String uidMapBody = runTimeContext.getSdkRequest().getOrDefault(SdkParam.U_ID_MAP.getParam(), "");
        if (StringUtils.isBlank(uidMapBody)) {
            runTimeContext.getTermCodes().add(TermCode.DEVICE_ID_NOT_FOUND);
        }
        try {
            if (osType == OsType.IOS) {
                //ios u-id-map不加密
                uidMap = ObjectMapperUtils.fromJSON(uidMapBody, Map.class);
            } else {
                if (runTimeContext.isSecureMessageFlag()) {
                    //安卓加密请求，u-id-map不加密
                    uidMap = ObjectMapperUtils.fromJSON(uidMapBody, Map.class);
                } else {
                    //安卓不加密请求，u-id-map加密
                    String randomKey = runTimeContext.getSdkRequest().get(SdkParam.U_ID_KEY.getParam());
                    uidMap = decryptRSA(runTimeContext,uidMapBody, randomKey);
                }
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"parse SDK idMapping error {}", e);
            runTimeContext.getTermCodes().add(TermCode.SDK_PARAM_INVALID);
        }
        if (MapUtils.isEmpty(uidMap)) {
            runTimeContext.getTermCodes().add(TermCode.DEVICE_ID_NOT_FOUND);
        }
        return uidMap;
    }

    private void checkIds(RunTimeContext runTimeContext, Device.IdMapping idMapping, boolean needCheckIdfa) {
        Device device = runTimeContext.getDevice();
        if (device.getOsType() == OsType.ANDROID) {
            if (checkAndroidIdsInvalid(idMapping)) {
                runTimeContext.getTermCodes().add(TermCode.ANDROID_ID_INVALID);
            }
        } else {
            if (needCheckIdfa) {
                checkIosIds(runTimeContext, idMapping);
            }
        }
    }

    private void checkIosIds(RunTimeContext runTimeContext, Device.IdMapping idMapping) {
        /*if (StringUtils.isBlank(idMapping.getIdfa())) {
            runTimeContext.getTermCodes().add(TermCode.IOS_ID_EMPTY);
        }*/
        if (StringUtils.isNotBlank(idMapping.getIdfa())
                && (idMapping.getIdfa().equals(ZERO) || invalidIDFA(idMapping.getIdfa()))) {
            runTimeContext.getTermCodes().add(TermCode.INVALID_IDFA);
        }
    }

    private boolean checkAndroidIdsInvalid(Device.IdMapping idMapping) {
        boolean o1Invalid = false;
        boolean oaidInvalid = false;
        boolean imeiSha1Invalid = false;
        boolean imeiMd5Invalid = false;
        boolean imeiInvalid = false;
        boolean um5Invalid = false;
        boolean oaidMd5Invalid = false;
        if (StringUtils.isNotBlank(idMapping.getO1()) && invalidO1(idMapping.getO1())) {
            o1Invalid = true;
            idMapping.setO1("");
        }
        if (StringUtils.isNotBlank(idMapping.getOaid()) && (invalidOAId(idMapping.getOaid()) || idMapping.getOaid().equals(ZERO))) {
            oaidInvalid = true;
            idMapping.setOaid("");
        }
        if (StringUtils.isNotBlank(idMapping.getImeiSha1()) && invalidSha1IMEI(idMapping.getImeiSha1())) {
            imeiSha1Invalid = true;
            idMapping.setImeiSha1("");
        }
        if (StringUtils.isNotBlank(idMapping.getImeiMd5()) && invalidMd5IMEI(idMapping.getImeiMd5())) {
            imeiMd5Invalid = true;
            idMapping.setImeiMd5("");
        }
        if (StringUtils.isNotBlank(idMapping.getImei()) && invalidIMEI(idMapping.getImei())) {
            imeiInvalid = true;
            idMapping.setImei("");
        }
        if (StringUtils.isNotBlank(idMapping.getUm5()) && invalidUM5(idMapping.getUm5())) {
            um5Invalid = true;
            idMapping.setUm5("");
        }
        if (StringUtils.isNotBlank(idMapping.getOaidMd5()) && invalidOaidMd5(idMapping.getOaidMd5())) {
            oaidMd5Invalid = true;
            idMapping.setOaidMd5("");
        }
        return o1Invalid && oaidInvalid && imeiSha1Invalid && imeiMd5Invalid && imeiInvalid && um5Invalid && oaidMd5Invalid;
    }

    private boolean invalidO1(String o1) {
        return !validO1.matcher(o1).matches();
    }

    private boolean invalidOAId(String oaid) {
        return !validOAID.matcher(oaid).matches();
    }

    private boolean invalidIDFA(String idfa) {
        return !validIDFA.matcher(idfa).matches();
    }

    private boolean invalidMd5IMEI(String md5IMEI) {
        if (md5IMEI.length() != MD5_HASH_LENGTH
                || nonAlphaNumeric.matcher(md5IMEI).matches()
                || INVALID_IMEI_MD5.contains(StringUtils.lowerCase(md5IMEI))) {
            return true;
        }
        return false;
    }

    private boolean invalidOaidMd5(String oaidMd5) {
        if (oaidMd5.length() != MD5_HASH_LENGTH
                || nonAlphaNumeric.matcher(oaidMd5).matches()) {
            return true;
        }
        return false;
    }


    private boolean invalidSha1IMEI(String sha1IMEI) {
        if (sha1IMEI.length() != SHA1_HASH_LENGTH || nonAlphaNumeric.matcher(sha1IMEI).matches()) {
            return true;
        }
        return false;
    }

    private boolean invalidIMEI(String imei) {
        if (imei.length() != IMEI_LENGTH || nonAlphaNumeric.matcher(imei).matches()) {
            return true;
        }
        return false;
    }

    private boolean invalidIDFV(String idfv) {
        return !validIDFV.matcher(idfv).matches() || ZERO.equals(idfv);
    }

    private boolean invalidUM5(String um5) {
        if (um5.length() != UM5_LENGTH || nonAlphaNumeric.matcher(um5).matches()) {
            return true;
        }
        return false;

    }

    private Map<String, String> decryptRSA(RunTimeContext runTimeContext, String uidMapValue, String randomKey) {
        Map<String, String> uidMap = new HashMap<>();
        try {
            //TODO BY REMI
            Cipher cipherTest = Cipher.getInstance("RSA/ECB/nopadding");
            cipherTest.init(Cipher.DECRYPT_MODE, uidRSAPrivateKey);
            byte[] bts = Base64.decode(uidMapValue.getBytes(), Base64.DEFAULT);
            byte[] decrypted = blockCipher(bts, cipherTest);
            String originalUidValue = new String(decrypted, StandardCharsets.UTF_8);
            originalUidValue = originalUidValue.replaceAll("\u0000", "");
            JSONObject jsonObject = new JSONObject(originalUidValue);
            for (UidType uidType : UidType.values()) {
                if (jsonObject.has(uidType.getValue())) {
                    String value = (String) jsonObject.get(uidType.getValue());
                    String xorDecrypt = uidType.isNeedXorDecrypted() ? xorWithKey(runTimeContext,value, randomKey) : value;
                    uidMap.put(uidType.getValue(), xorDecrypt);
                }
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"decryptRSA uidMap error {}", e);
        }
        return uidMap;
    }

    private byte[] blockCipher(byte[] bytes, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {

        byte[] scrambled;
        byte[] toReturn = new byte[0];

        int length = 64;
        int actualLength = bytes.length;

        byte[] buffer = new byte[length];

        for (int i = 0; i < actualLength; i++) {
            if ((i > 0) && (i % length == 0)) {
                scrambled = cipher.doFinal(buffer);
                toReturn = append(toReturn, scrambled);
                int newlength = length;
                if (i + length > actualLength) {
                    newlength = actualLength - i;
                }
                buffer = new byte[newlength];
            }
            buffer[i % length] = bytes[i];
        }

        scrambled = cipher.doFinal(buffer);
        toReturn = append(toReturn, scrambled);

        return toReturn;
    }

    private byte[] append(byte[] prefix, byte[] suffix) {
        byte[] toReturn = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, toReturn, 0, prefix.length);
        System.arraycopy(suffix, 0, toReturn, prefix.length, suffix.length);
        return toReturn;
    }

    private String xorWithKey(RunTimeContext runTimeContext, String value, String randomkey) {
        if (value == null) {
            return null;
        }
        String fvaluexor = "";
        try {
            byte[] valuebyte;
            valuebyte = value.getBytes("UTF-8");
            valuebyte = Base64.decode(valuebyte, Base64.NO_WRAP);

            byte[] valuexor = new byte[valuebyte.length];
            byte[] key = randomkey.getBytes("UTF-8");

            for (int i = 0; i < valuebyte.length; i++) {
                valuexor[i] = (byte) (valuebyte[i] ^ key[i % key.length]);
            }
            fvaluexor = new String(valuexor, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"xorWithKey error {}", e);
        }
        return fvaluexor;
    }

    private void addUserDeviceId(Map<Integer, String> userDeviceIdMap, Pair<Integer, String> ...deviceIdPairs) {
        if (ArrayUtils.isNotEmpty(deviceIdPairs)) {
            Arrays.stream(deviceIdPairs)
                    .filter(deviceIdPair -> deviceIdPair.getLeft() != null && StringUtils.isNotBlank(deviceIdPair.getRight()))
                    .forEach(deviceIdPair -> userDeviceIdMap.put(deviceIdPair.getKey(), deviceIdPair.getValue()));
        }
    }
}