package com.inmobi.supply.sun.service.preProc.normalizer.module;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;

import cn.inmobi.pb.api.Carrier;
import java.util.Objects;
import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.api.ortb.ApiOrtbRequest;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.google.gson.JsonArray;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.adapterlibrary.entity.RequestExt;
import com.inmobi.supply.adapterlibrary.request.api.CustomIntegrationIdentifier;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.ApiOrtbContext;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.CnConfigRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.preProc.normalizer.module.strategy.NormalizerRequestWorker;
import com.inmobi.supply.sun.service.preProc.normalizer.module.strategy.NormalizerRequestWorkerFactory;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.SecureSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.ProtocolConstant.*;

/**
 * extract request param
 *
 * @author Gavin.xu
 * @date 2020.5.18
 */
@Service
@Slf4j
public class NormalizerRequest implements ModuleWorker<Integer> {




    @Autowired
    private NormalizerRequestWorkerFactory normalizerRequestWorkerFactory;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {


        RequestType requestType = runTimeContext.getRequestType();
        try {
            if (StringUtils.isBlank(runTimeContext.getRequestBody())) {
                throw new SunException(TermCode.BODY_EMPTY, runTimeContext);
            }
            NormalizerRequestWorker normalizerRequestWorker = normalizerRequestWorkerFactory.get(requestType);
            normalizerRequestWorker.parseRequest(runTimeContext);
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