package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;


import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static com.inmobi.supply.sun.constant.ProtocolConstant.*;
import static com.inmobi.supply.sun.constant.RequestType.*;

/**
 * @Description NormalizerRequestV3Worker
 * @Date 2022/5/25 15:54
 * @Author william.wang
 */
@Component
@Builder
public class NormalizerRequestV3Worker implements NormalizerRequestWorker {

    @Override
    public void parseRequest(RunTimeContext runTimeContext) throws SunException {
        MediaType mediaType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.APPLICATION_JSON);
        ApiV3Request apiV3Request = ApiV3Request.getDefaultInstance();
        String requestBody = runTimeContext.getRequestBody();
        LogUtils.logInfo(runTimeContext.getRequestId(), "apiV3Request api body:{}", requestBody);
        try {
            //确定接入协议
            ProtocolConstant protocol = getProtocolBuyContentType(mediaType);
            if (protocol == JSON) {
                if (StringUtils.isBlank(requestBody)) {
                    throw new SunException(TermCode.BODY_EMPTY, runTimeContext);
                }
                ApiV3Request.Builder apiV3RequestBuilder = ApiV3Request.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(requestBody, apiV3RequestBuilder);
                String carrierName = apiV3RequestBuilder.getDevice().getCarrierName();
                switch (carrierName) {
                    case "1":
                        apiV3RequestBuilder.getDeviceBuilder().setCarrier("ChinaMobile");
                        break;
                    case "2":
                        apiV3RequestBuilder.getDeviceBuilder().setCarrier("ChinaUnicom");
                        break;
                    case "3":
                        apiV3RequestBuilder.getDeviceBuilder().setCarrier("ChinaTelecom");
                        break;
                    default:
                }
                apiV3Request = apiV3RequestBuilder.build();
            }
            if (protocol == PROTO_BUF) {
                //future maybe from JOSN -> Protobuf
                apiV3Request = ApiV3Request.parseFrom(runTimeContext.getInputStream());
            }
            if (apiV3Request == null) {
                throw new SunException(TermCode.BODY_API3_INVALID, runTimeContext);
            }
            BaseRequest.Builder baseRequestBuilder = BaseRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(JsonFormat.printer().print(apiV3Request), baseRequestBuilder);
            runTimeContext.setBaseRequest(baseRequestBuilder.build());
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "parseAPI3VRequest error! error:{}", e);
            throw new SunException(TermCode.BODY_API3_INVALID, runTimeContext);
        }
    }


    @Override
    public List<RequestType> getRequestTypeList() {
        return Arrays.asList(API_3_1, API_3_3, API_3_2, API_3_3_1, API_3_4, API_3_4_1);
    }
}