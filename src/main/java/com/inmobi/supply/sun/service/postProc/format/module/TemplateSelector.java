package com.inmobi.supply.sun.service.postProc.format.module;

import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.api.MaterialMeta;
import com.inmobi.supply.component.model.redis.TemplateModel;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.postProc.format.module.template.*;
import com.inmobi.supply.sun.service.postProc.format.module.template.api.*;
import com.inmobi.supply.sun.service.postProc.format.module.template.sdk.SdkEnvelopWorker;
import com.inmobi.supply.sun.service.postProc.format.module.template.sdk.SdkHtmlEnvelopWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;

import static java.util.stream.Collectors.toList;

/**
 * select template from ad pool response
 *
 * @author remi.li
 */
@Service
@Slf4j
public class TemplateSelector implements ModuleWorker<IEnvelop> {
    @Autowired
    private SdkEnvelopWorker sdkEnvelopWorker;
    @Autowired
    private SdkHtmlEnvelopWorker sdkHtmlEnvelopWorker;
    @Autowired
    private ApiV31EnvelopWorker apiV31EnvelopWorker;
    @Autowired
    private ApiV33EnvelopWorker apiV33EnvelopWorker;
    @Autowired
    private ApiV4EnvelopWorker apiV4EnvelopWorker;
    @Autowired
    private ApiV4JsonEnvelopWorker apiV4JsonEnvelopWorker;
    @Autowired
    private ApiHtmlEnvelopWorker apiHtmlEnvelopWorker;
    @Autowired
    private ApiVCustomEnvelopWorker apiVCustomEnvelopWorker;

    @Override
    public Mono<IEnvelop> processMono(RunTimeContext runTimeContext) {
        PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "TemplateSelector");
        LogUtils.logInfo(runTimeContext.getRequestId(),"enter Envelope2Service TemplateSelector");
        IEnvelop envelop = null;
        try {
            /**
             * api渲染(后续api渲染可能要支持H5)
             */
            if (!runTimeContext.getRequestType().isSdk()){
                RequestType requestType = runTimeContext.getRequestType();
                switch (requestType) {
                    case API_3_1:
                        envelop = apiV31EnvelopWorker;
                        break;
                    case API_3_3:
                    case API_3_3_1:
                    case API_3_4:
                    case API_3_4_1:
                        envelop = apiV33EnvelopWorker;
                        break;
                    case API_4_0:
                        ProtocolConstant contentType = runTimeContext.getContentType();
                        if(ProtocolConstant.PROTO_BUF == contentType){
                            envelop = apiV4EnvelopWorker;
                        }else {
                            envelop = apiV4JsonEnvelopWorker;
                        }
                        break;
                    case CUSTOM_API:
                        envelop = apiVCustomEnvelopWorker;
                        break;
                    default:
                }
                runTimeContext.setEnvelop(envelop);
                return Mono.just(envelop);
            }
            /**
             * sdk渲染，先选择模版
             */
            if (runTimeContext.getRequestType().isSdk()){
                MaterialMeta materialMeta = runTimeContext.getWinAd().getCreative();
                PlacementModel placementModel = runTimeContext.getPlacementModel();
                List<TemplateModel> templateModelList = placementModel.getTemplateList();
                float creativeImageWidthHeightRatio = 0;
                if (materialMeta.getCreativeType() == CreativeType.IMAGE_VALUE
                        || materialMeta.getCreativeType() == CreativeType.GIF_VALUE) {
                    creativeImageWidthHeightRatio = (float) (materialMeta.getImage().getWidth() / materialMeta.getImage().getHeight());
                }
                //1. select by creativeType
                float finalCreativeImageWidthHeightRatio = creativeImageWidthHeightRatio;
                List<TemplateModel> selectTemplateModels = templateModelList
                        .stream()
                        .filter(templateModel -> templateModel.getDcCreativeTypeId() == materialMeta.getCreativeType())
                        .peek(templateModel -> {
                            if (templateModel.getDcCreativeTypeId() == CreativeType.IMAGE_VALUE) {
                                int width = Integer.valueOf(StringUtils.split(templateModel.getDcAspectRatio(), ":")[0]);
                                int height = Integer.valueOf(StringUtils.split(templateModel.getDcAspectRatio(), ":")[1]);
                                float widthHeightRatio = (float) (width / height);
                                float imageWidthHeightRatio = Math.abs((widthHeightRatio - finalCreativeImageWidthHeightRatio) / finalCreativeImageWidthHeightRatio);
                                templateModel.setImageWidthHeightRatio(imageWidthHeightRatio);
                            }
                        }).collect(toList());
                if (materialMeta.getCreativeType() == CreativeType.VIDEO_VALUE) {
                    //random pick one
                    Random n = new Random();
                    TemplateModel selectTemplateModel = selectTemplateModels.get(n.nextInt(selectTemplateModels.size()));
                    runTimeContext.setFinalTemplate(selectTemplateModel);
                } else {
                    float minRatio = selectTemplateModels.get(0).getImageWidthHeightRatio();
                    TemplateModel minRatioTemplate = selectTemplateModels.get(0);
                    for (TemplateModel templateModel : selectTemplateModels) {
                        if (templateModel.getImageWidthHeightRatio() < minRatio) {
                            minRatioTemplate = templateModel;
                        }
                    }
                    runTimeContext.setFinalTemplate(minRatioTemplate);
                }
                /**
                 * 渲染方式 2:H5 1:json
                 */
                if (runTimeContext.getPlacementModel().getRenderType() == 2) {
                    switch (runTimeContext.getAdType()) {
                        case BANNER:
                        case INTERSTITIAL:
                        case REWARD:
                            runTimeContext.setEnvelop(sdkHtmlEnvelopWorker);
                            envelop = sdkHtmlEnvelopWorker;
                            break;
                        default:
                            runTimeContext.setEnvelop(sdkEnvelopWorker);
                            envelop = sdkEnvelopWorker;
                            break;
                    }
                } else {
                    runTimeContext.setEnvelop(sdkEnvelopWorker);
                    envelop = sdkEnvelopWorker;
                }
                return Mono.just(envelop);
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "templateSelector error {}", e);
        }
        return Mono.just(apiV31EnvelopWorker);
    }
}