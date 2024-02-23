package com.inmobi.supply.sun.service.preProc.validator.module;

import java.util.*;

import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import cn.inmobi.pb.sun.rr.base.Device;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.BlackListPlacementRepo;
import com.inmobi.supply.sun.repo.file.DeprecatedPlacementRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.ID_CONFIDENCE_PLACEMENT;
import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.PLACEMENT_SPLIT_MAPPING;
import static com.inmobi.supply.sun.constant.SdkParam.D_UA;

/**
 * Gavin.xu
 * 2020.5.18
 * 参数校验
 */
@Service
@Slf4j
public class ValidatorRequestField implements ModuleWorker<Integer> {
    @Autowired
    private DeprecatedPlacementRepo deprecatedPlacementRepo;
    @Autowired
    private BlackListPlacementRepo blackListPlacementRepo;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"ValidatorRequestField");
        try {
            switch (runTimeContext.getRequestType()) {
                case SDK:
                    validateSdk(runTimeContext.getSdkRequest(), runTimeContext);
                    break;
                case API_4_0:
                    validateApi4(runTimeContext.getBaseRequest(), runTimeContext);
                case API_3_1:
                case API_3_3:
                case API_3_3_1:
                case API_3_4:
                case API_3_4_1:
                case CUSTOM_API:
                case API_ORTB_1_0:
                    validateApi(runTimeContext.getBaseRequest(), runTimeContext);
                    break;
                default:
                    break;
            }
            replaceOverLay(runTimeContext);
            return Mono.just(0);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    /**
     * V4对部分参数进行严格校验
     *
     * @param baseRequest
     * @param runTimeContext
     */
    private void validateApi4(BaseRequest baseRequest, RunTimeContext runTimeContext) throws SunException {

        // adtype
        if (baseRequest.getSite().getAdType() ==0) {
            throw new SunException(TermCode.AD_TYPE_INVALID, runTimeContext);
        }
        // tamx
        if (baseRequest.getTmax() < RequestFieldConstant.T_MAX_FLOOR || baseRequest.getTmax() > RequestFieldConstant.T_MAX_UPPER) {
            throw new SunException(TermCode.T_MAX_INVALID, runTimeContext);
        }
        //model make ua
        if (StringUtils.isBlank(baseRequest.getDevice().getUa())) {
            throw new SunException(TermCode.UA_INVALID, runTimeContext);
        }
        if (StringUtils.isBlank(baseRequest.getDevice().getMake())){
            throw new SunException(TermCode.MAKE_INVALID, runTimeContext);
        }
        if (StringUtils.isBlank(baseRequest.getDevice().getModel())) {
            throw new SunException(TermCode.MODEL_INVALID, runTimeContext);
        }
        //connectionType
        if (baseRequest.getDevice().getConnectiontype() == RequestFieldConstant.CONNECTION_TYPE_UNRECOGNIZED) {
            throw new SunException(TermCode.CONNECTION_TYPE_INVALID, runTimeContext);
        }
        //deviceType
        if (baseRequest.getDevice().getType() == RequestFieldConstant.DEVICE_TYPE_KNOWN) {
            throw new SunException(TermCode.DEVICE_TYPE_INVALID, runTimeContext);
        }
        //BootTimeSecond
        String bootTimeSecond = baseRequest.getDevice().getBootTimeSecond();
        if (StringUtils.isNotBlank(bootTimeSecond)) {
            String[] bootTimeSecondSplit = StringUtils.split(bootTimeSecond, SymbolString.POINT);
            if (bootTimeSecondSplit.length != RequestFieldConstant.COMMON_INT_1) {
                //throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
                Device.Builder deviceBuilder = baseRequest.getDevice().toBuilder();
                deviceBuilder.clearBootTimeSecond();
            }
        }
        //OsUpdateTimeSecond
        String osUpdateTimeSecond = baseRequest.getDevice().getOsUpdateTimeSecond();
        if (StringUtils.isNotBlank(osUpdateTimeSecond)) {
            String[] osUpdateTimeSecondSplit = StringUtils.split(osUpdateTimeSecond, SymbolString.POINT);
            if (osUpdateTimeSecondSplit.length != RequestFieldConstant.COMMON_INT_2 || osUpdateTimeSecondSplit[0].length() != RequestFieldConstant.COMMON_INT_10 || osUpdateTimeSecondSplit[1].length() != RequestFieldConstant.COMMON_INT_6) {
                //throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
                Device.Builder deviceBuilder = baseRequest.getDevice().toBuilder();
                deviceBuilder.clearOsUpdateTimeSecond();
            }
        }
        //osVersion
        /*String osv = baseRequest.getDevice().getOsv();
        if (StringUtils.isBlank(osv)) {
            throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
        }
        String[] osvSplit = StringUtils.split(osv, SymbolString.POINT);
        String majorVersion = osvSplit[0];
        if (!StringUtils.isNumeric(majorVersion)) {
            throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
        }
        int majorVersionValue = Integer.parseInt(majorVersion);
        if (RequestFieldConstant.OS_ANDROID.equalsIgnoreCase(baseRequest.getDevice().getOs())) {
            if (majorVersionValue < RequestFieldConstant.OS_LIMIT_ANDROID_FLOOR || majorVersionValue > RequestFieldConstant.OS_LIMIT_ANDROID_UPPER) {
                throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
            }
        }
        if (RequestFieldConstant.OS_IOS.equalsIgnoreCase(baseRequest.getDevice().getOs())) {
            if (majorVersionValue < RequestFieldConstant.OS_LIMIT_IOS_FLOOR || majorVersionValue > RequestFieldConstant.OS_LIMIT_IOS_UPPER) {
                throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
            }
        }*/
    }

    private void validateSdk(Map<String, String> map, RunTimeContext runTimeContext) throws SunException {
        LogUtils.logInfo(runTimeContext.getRequestId(),"validateSdk");
        //1. check ua
        normalSdkUa(runTimeContext);
        //2. check placementId
        String placementId = map.get(SdkParam.IM_PLID.getParam());
        checkPlacementId(runTimeContext, placementId);
        //3. get app bundle
        String bundle = map.get(SdkParam.U_APPBID.getParam());
        runTimeContext.setRequestBundleId(bundle);
    }

    private void validateApi(BaseRequest baseRequest, RunTimeContext runTimeContext) throws SunException {
        normalAPIV3Ua(runTimeContext);
        //1. check placementId
        String placementId = baseRequest.getApp().getId();
        checkPlacementId(runTimeContext, placementId);
        //2. get app bundle
        String bundle = baseRequest.getApp().getBundle();
        runTimeContext.setRequestBundleId(bundle);
        //3. check if api need https
        if (runTimeContext.getBaseRequest().getImp().getSecure() == 1) {
            runTimeContext.setSecure(true);
        } else {
            runTimeContext.setSecure(false);
        }
        // 4. check app version
        String ver = baseRequest.getApp().getVer();
        final String[] split = StringUtils.split(ver, ".");
        // 要求版本为数字
        boolean versionValidate = true;
        for (String s : split) {
            if (!StringUtils.isNumeric(s)) {
                versionValidate = false;
            }
        }
        // 三个版本号可以部分为空
        if (versionValidate) {
            if (split.length >= 3) {
                runTimeContext.setAppVersionMicro(Integer.valueOf(split[2]));
            }
            if (split.length >= 2) {
                runTimeContext.setAppVersionMinor(Integer.valueOf(split[1]));
            }
            if (split.length >= 1) {
                runTimeContext.setAppVersionMajor(Integer.valueOf(split[0]));
            }
        }

    }

    private void normalAPIV3Ua(RunTimeContext runTimeContext) {
        String useAgent = runTimeContext.getBaseRequest().getDevice().getUa();
        if (StringUtils.isNotBlank(useAgent)) {
            runTimeContext.setUserAgent(useAgent);
            runTimeContext.setUAPresent(true);
            runTimeContext.setUaSourceType(UaSourceType.REQUEST_BODY);
        }
    }

    private void normalSdkUa(RunTimeContext runTimeContext) {
        String useAgent = runTimeContext.getHeaders().get(StringUtils.lowerCase(RequestHeader.NORMAL_USER_AGENT));
        if (StringUtils.isNotBlank(useAgent)) {
            runTimeContext.setUserAgent(useAgent);
            runTimeContext.setUAPresent(true);
            runTimeContext.setUaSourceType(UaSourceType.HEADER);
            runTimeContext.getSdkRequest().put(D_UA.of(), useAgent);
        }
    }


    private void checkPlacementId(RunTimeContext runTimeContext, String placementId) throws SunException {
        if (StringUtils.isBlank(placementId)) {
            throw new SunException(TermCode.PLACEMENT_ID_REQUIRED, runTimeContext);
        }
        try {
            long placementIdL = Long.parseLong(placementId);

            //特殊配置广告位流量均分
            Map<?, ?> map = PLACEMENT_SPLIT_MAPPING.get();
            List<String> listPlacements = (List<String>) map.get(placementId);
            if(CollectionUtils.isNotEmpty(listPlacements)){
                PerfUtils.perf("sun.replace.placement","placementId",placementId);
                LogUtils.logInfo(runTimeContext.getRequestId(),"replacePlacementMapping:{}",JSON.toJSONString(listPlacements));
                String placementIdReplace = randomPlacementId(listPlacements);
                runTimeContext.setPlacementId(Long.valueOf(placementIdReplace));
                PerfUtils.perf("sun.replace.placement","placementId",placementIdReplace);
            }else {
                runTimeContext.setPlacementId(placementIdL);
            }

            if (Optional.ofNullable(deprecatedPlacementRepo.getDeprecatedPlacementIdList()).orElse(new ArrayList<>()).contains(placementIdL)) {
                throw new SunException(TermCode.DEPRECATED_PLACEMENT_ID, runTimeContext);
            }
            // 校验placementId是否为黑名单中的id
            if (Optional.ofNullable(blackListPlacementRepo.getBlackPlacementIdSet()).orElse(new HashSet<Long>()).contains(placementIdL)) {
                throw new SunException(TermCode.PLACEMENT_ID_FORBIDDEN, runTimeContext);
            }
        } catch (NumberFormatException e) {
            LogUtils.logError("checkPlacementId error {}", runTimeContext.getRequestId(), e);
            throw new SunException(TermCode.PLACEMENT_ID_INVALID, runTimeContext);
        }
    }

    private void replaceOverLay(RunTimeContext runTimeContext) {
        if (runTimeContext.getAdType() == null) {
            return;
        }
        if (runTimeContext.getAdType() == AdType.OVERLAY) {
            runTimeContext.setAdType(AdType.FEED);
        }
    }

    private String randomPlacementId(List<String> listPlacementId){
        int index = new Random().nextInt(listPlacementId.size());
        return listPlacementId.get(index);
    }

}