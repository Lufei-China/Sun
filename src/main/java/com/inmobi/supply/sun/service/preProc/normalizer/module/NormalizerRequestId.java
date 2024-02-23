package com.inmobi.supply.sun.service.preProc.normalizer.module;

import java.util.Random;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.framework.common.utils.sfidgen.SfIdGen;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.SdkParam;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * gen requestId
 *
 * @author Gavin.xu
 * @date 2020.5.18
 */
@Service
@Slf4j
public class NormalizerRequestId implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"NormalizerRequestId");
            //String requestId = runTimeContext.getRequestId();
            RequestType requestType = runTimeContext.getRequestType();
            if (requestType == RequestType.SDK) {
                String sdkRequestId = runTimeContext.getSdkRequest().getOrDefault(SdkParam.CLIENT_REQUEST_ID.getParam(), "");
                if (StringUtils.isBlank(sdkRequestId)) {
                    throw new SunException(TermCode.SDK_REQUEST_ID_EMPTY, runTimeContext);
                }
                LogUtils.logInfo(runTimeContext.getRequestId(),"sdk requestId {} replace genId {}", sdkRequestId, runTimeContext.getRequestId());
                runTimeContext.setRequestId(sdkRequestId);
                //sdk version
                String mkVersion = runTimeContext.getSdkRequest().getOrDefault(SdkParam.MK_VERSION.getParam(), "");
                if (StringUtils.isBlank(mkVersion)) {
                    throw new SunException(TermCode.SDK_VERSION_EMPTY, runTimeContext);
                }
                String[] splitSdkVersion = StringUtils.split(mkVersion, "-");
                if (splitSdkVersion.length < 3) {
                    //长度小于3肯定没有版本号
                    throw new SunException(TermCode.SDK_VERSION_EMPTY, runTimeContext);
                }
                String sdkVersion = StringUtils.upperCase(splitSdkVersion[2]);
                if (StringUtils.isBlank(sdkVersion)) {
                    throw new SunException(TermCode.SDK_VERSION_EMPTY, runTimeContext);
                }

                StringBuilder sdkNumVersion = new StringBuilder();
                char[] ver = sdkVersion.toCharArray();
                for (char ch : ver) {
                    if (ch == 'T') {
                        sdkNumVersion.append(".");
                    } else {
                        sdkNumVersion.append(ch - 'A');
                    }
                }
                runTimeContext.setSdkVersion(sdkNumVersion.toString());
            }/* else {
                //API Request
                //requestId = genRequestId();
                if (StringUtils.isBlank(runTimeContext.getRequestId())) {
                    throw new SunException(TermCode.API_REQUEST_ID_EMPTY, runTimeContext);
                }
            }
            runTimeContext.setRequestId(requestId);*/
            return Mono.just(0);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }


}