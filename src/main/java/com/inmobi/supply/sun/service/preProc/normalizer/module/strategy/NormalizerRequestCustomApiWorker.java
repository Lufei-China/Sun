
package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;


import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.adapterlibrary.entity.RequestExt;
import com.inmobi.supply.adapterlibrary.request.api.CustomIntegrationIdentifier;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.CnConfigRedisRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.inmobi.supply.sun.constant.RequestType.CUSTOM_API;

/**
 * @Description NormalizerRequestCustomApiWorker
 * @Date 2022/5/25 15:54
 * @Author william.wang
 */
@Component
@Builder
public class NormalizerRequestCustomApiWorker implements NormalizerRequestWorker {


    @Autowired
    private CnConfigRedisRepo cnConfigRedisRepo;

    @Override
    public void parseRequest(RunTimeContext runTimeContext) throws SunException {
        RequestExt ext = new RequestExt();
        ext.setRequestHost(runTimeContext.getHost());
        ext.setRequestRawPath(runTimeContext.getRawPath());
        ServerRequest request = runTimeContext.getServerRequest();
        String adapterName = request.uri().getRawPath().substring("/showad/".length());
        if("zhihu2".equals(adapterName) || "zhihu1".equals(adapterName)) adapterName = "zhihu";
        if("wpsrtb".equals(adapterName)) adapterName = "wps";
        //获取指定adapter的配置信息
        JSONObject cnConfig = cnConfigRedisRepo.getCnConfigMap().get(adapterName);
        JSONObject jsonObject = new JSONObject();
        HashMap<String, JSONObject> stringJSONObjectHashMap = new HashMap<>();
        stringJSONObjectHashMap.put(adapterName,cnConfig);
        jsonObject.put("customApiIntegrations",stringJSONObjectHashMap);
        jsonObject.put("iosModuleMapping",cnConfigRedisRepo.getIosModuleMapping());
        jsonObject.put("androidModuleMapping",cnConfigRedisRepo.getAndroidModuleMapping());
        ext.setCnConfig(jsonObject.toJSONString());
        ApiV3Request apiV3Request;
        String requestBody = null;
        try {
            RequestAdapterResponse requestAdapterResponse = CustomIntegrationIdentifier.normalize(runTimeContext.getServerRequest(), runTimeContext.getRequestBody(), runTimeContext.getInputStream(), ext, runTimeContext.getTraceMarker());
            LogUtils.logInfo(runTimeContext.getRequestId(),"adapterHostUrl {},adapterBody {},adapterInputStream {}",runTimeContext.getRawPath(),runTimeContext.getRequestBody(),runTimeContext.getInputStream());
            runTimeContext.setRequestAdapterResponse(requestAdapterResponse);
            requestBody = requestAdapterResponse.getApiV3Request();
            if (runTimeContext.getRequestAdapterResponse() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("floor") != null) {
                runTimeContext.setBaiduBrice(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("floor").getAsLong());
            }

            //添加百度bes 竞价方式监控
            try{
                if (runTimeContext.getRawPath().endsWith("/bes") && runTimeContext.getRequestAdapterResponse() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields() != null) {
                    if(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("besAttributeInfo") != null){
                        String attributeInfo = runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("besAttributeInfo").toString();
                        runTimeContext.setAttributeInfo(attributeInfo);
                        PerfUtils.perf("sun.bes.attribute","type",attributeInfo);
                    } else {
                        PerfUtils.perf("sun.bes.attribute","type","-1");
                    }
                }
            }catch (Exception e){
                PerfUtils.perf("sun.bes.attribute","type","-2");
                LogUtils.logError(runTimeContext.getRequestId(),"attributeInfoError",e);
            }

            if (runTimeContext.getRequestAdapterResponse() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("tagIdList") != null) {
                JsonArray jsonElements = runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("tagIdList").getAsJsonArray();
                if (jsonElements != null && jsonElements.size() > 0) {
                    jsonElements.forEach(bean -> {
                        runTimeContext.getBaiduTagIdList().add(bean.getAsString());
                    });
                }
            }
            if (runTimeContext.getRequestAdapterResponse() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("userCategory") != null) {
                JsonArray jsonElements = runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("userCategory").getAsJsonArray();
                if (jsonElements != null && jsonElements.size() > 0) {
                    jsonElements.forEach(bean -> {
                        runTimeContext.getBesUserCategory().add(bean.getAsLong());
                    });
                }
            }
            //针对 ortb 增加 bidFloor tmax, 同时进行美元和元的换算
            if(null != runTimeContext.getRequestAdapterResponse()
                    && null != runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields()
                    && null != runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("bidfloor")
                    && null != runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("bidfloorcur")
                    && "USD".equals(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("bidfloorcur").getAsString())){
                double bidfloor = runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("bidfloor").getAsDouble();
                String usdCny = DynamicStringConfig.USD_TO_CNY.get();
                BigDecimal multiply = new BigDecimal(bidfloor).multiply(new BigDecimal(usdCny)).multiply(new BigDecimal("100"));
                runTimeContext.setOrtbBidFloor(multiply.setScale(0, BigDecimal.ROUND_HALF_UP).longValue());
            }
            //ortbTmax
            if(null != runTimeContext.getRequestAdapterResponse()
                    && null != runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields()
                    && null != runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("tmax")){
                runTimeContext.setTmax(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("tmax").getAsInt());
            }
            ApiV3Request.Builder apiV3RequestBuilder = ApiV3Request.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(requestBody, apiV3RequestBuilder);
            apiV3Request = apiV3RequestBuilder.build();
            LogUtils.logInfo(runTimeContext.getRequestId(), "apiV3Request adapter body:{}", apiV3Request);
            BaseRequest.Builder baseRequestBuilder = BaseRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(JsonFormat.printer().print(apiV3Request), baseRequestBuilder);
            runTimeContext.setBaseRequest(baseRequestBuilder.build());
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), "parseCustomApiRequest error: {}", e);
            LogUtils.logInfo(runTimeContext.getRequestId(), "CustomApiRequest adapter error. adapterName {} requestBody {} adapterV3Request {}",adapterName, runTimeContext.getRequestBody(), requestBody);
            throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
        }
    }


    @Override
    public List<RequestType> getRequestTypeList() {
        return Collections.singletonList(CUSTOM_API);
    }
}