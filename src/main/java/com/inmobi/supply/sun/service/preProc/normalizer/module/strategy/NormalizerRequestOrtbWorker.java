
package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;


import cn.inmobi.pb.sun.rr.api.ortb.ApiOrtbRequest;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.ApiOrtbContext;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.CnConfigRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static com.inmobi.supply.sun.constant.ProtocolConstant.*;
import static com.inmobi.supply.sun.constant.RequestType.API_ORTB_1_0;

/**
 * @Description NormalizerRequestOrtbWorker
 * @Date 2022/5/25 15:54
 * @Author william.wang
 */
@Component
@Builder
public class NormalizerRequestOrtbWorker implements NormalizerRequestWorker {

    @Autowired
    private CnConfigRepo cnConfigRepo;

    @Override
    public void parseRequest(RunTimeContext runTimeContext) throws SunException {
        ApiOrtbRequest ortbRequest = ApiOrtbRequest.getDefaultInstance();
        MediaType mediaType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.APPLICATION_JSON);
        String requestBody = runTimeContext.getRequestBody();
        try {
            //确定接入协议
            ProtocolConstant protocol = getProtocolBuyContentType(mediaType);
            if (protocol == JSON) {
                if (StringUtils.isBlank(requestBody)) {
                    throw new SunException(TermCode.BODY_EMPTY, runTimeContext);
                }
                ApiOrtbRequest.Builder ortbRequestBuilder = ApiOrtbRequest.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(requestBody, ortbRequestBuilder);
                ortbRequest = ortbRequestBuilder.build();
            }
            if (protocol == PROTO_BUF) {
                ortbRequest = ApiOrtbRequest.parseFrom(runTimeContext.getInputStream());
            }
            if (ortbRequest == null) {
                throw new SunException(TermCode.BODY_API3_INVALID, runTimeContext);
            }
            runTimeContext.setApiContext(ApiOrtbContext.builder()
                    .apiOrtbRequest(ortbRequest)
                    .build());
        } catch (Exception e) {
            LogUtils.logError("NormalizerRequest parseAPI3VRequest error {}", runTimeContext.getRequestId(), e);
            throw new SunException(TermCode.BODY_API3_INVALID, runTimeContext);
        }
    }


    @Override
    public List<RequestType> getRequestTypeList() {
        return Arrays.asList(API_ORTB_1_0);
    }
}