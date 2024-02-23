package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import cn.inmobi.pb.sun.rr.base.User;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

import static cn.inmobi.pb.api.TrackingEvent.CLICK;
import static cn.inmobi.pb.api.TrackingEvent.VIEW;
import static cn.inmobi.pb.api.TrackingEvent.WIN_NOTICE;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.FIX_BID;

/**
 * Api3.1 && Api3.3 envelop
 *
 * @author andrew.Ji
 */
@Service
@Slf4j
public class ApiV31EnvelopWorker implements IEnvelop {


    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        try {
            FormatterModel formatterModel = runTimeContext.getFormatterModel();
            String formatAd;
            byte[] formatAdBytes = null;
            try {

                com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent pubContent31 = new com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent();
                pubContent31.setDescription(formatterModel.getDescription());
                pubContent31.setTitle(formatterModel.getTitle());
                pubContent31.setLandingURL(formatterModel.getLandingUrl());
                pubContent31.setCta(formatterModel.getCtaText());
                com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent.Image image31 = new com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent.Image();
                image31.setWidth((float) formatterModel.getImage().getWidth());
                image31.setHeight((float) formatterModel.getImage().getHeight());
                image31.setUrl(formatterModel.getImage().getUrl());
                image31.setAspectRatio(formatterModel.getImage().getAspectRatio().doubleValue());
                pubContent31.setScreenshots(image31);
                com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent.Image icon31 = new com.inmobi.supply.component.model.rr.ApiV31Response.Ads.PubContent.Image();

                icon31.setWidth((float) RuntimeConstant.DEFAULT_ICON_WIDTH);
                icon31.setHeight((float) RuntimeConstant.DEFAULT_ICON_HEIGHT);
                icon31.setUrl(formatterModel.getIconUrl());
                icon31.setAspectRatio(RuntimeConstant.DEFAULT_ICON_ASPECT_RATIO);
                pubContent31.setIcon(icon31);

                com.inmobi.supply.component.model.rr.ApiV31Response.Ads ads31 = new com.inmobi.supply.component.model.rr.ApiV31Response.Ads();
                ads31.setOpenExternal("DEEPLINK".equalsIgnoreCase(formatterModel.getOpenMode()));
                ads31.setIsApp(formatterModel.isApp());
                ads31.setLandingPage(formatterModel.getTargetUrl());
                ads31.setPubContent(pubContent31);

                Map<Integer, Map<String, List<String>>> eventTracking31 = new HashMap<>();
                runTimeContext.getAdRunTimeInfo().getEventTrackers()
                        .forEach((event, trackerUrls) -> {
                            Map<String, List<String>> trackerMap = new HashMap<>();
                            if (event == CLICK || event == VIEW || (event == WIN_NOTICE && Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1)) {
                                //api3.1 only support click and view
                                trackerMap.put("urls", trackerUrls);
                                eventTracking31.put(event.getNumber(), trackerMap);
                            }
                        });
                ads31.setEventTracking(eventTracking31);
//                        ads31.setBid(0L);
                if (FIX_BID.get().contains(runTimeContext.getPlacementId())) {
                    ads31.setBid(runTimeContext.getPlacementModel().getPubExpectation());
                }
//                        if (DYNAMIC_BID.get().contains(runTimeContext.getPlacementId())
//                                || Optional.ofNullable(runTimeContext.getPlacementModel().getIsBidSharing()).orElse(0) == 1) {
//                            //TODO 后期透传价格的媒体需要配置CI界面上
//                            //1. 有需要透传价格的媒体
//                            //2. 开启二价竞价的媒体需要透传价格
//                            ads31.setBid(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
//                        }
                ads31.setBidPassthrough(false);
                ads31.setBidSharing(0);
                User user = runTimeContext.getBaseRequest().getUser();
                if(CollectionUtils.isNotEmpty(user.getTagIdList())){
                    ads31.setTagId(user.getTagIdList());
                }
                // 透传价格
                if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getBidPassthrough() == 1) {
                    ads31.setBid(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));

                }
                com.inmobi.supply.component.model.rr.ApiV31Response apiV31Response = new com.inmobi.supply.component.model.rr.ApiV31Response();
                List<com.inmobi.supply.component.model.rr.ApiV31Response.Ads> ads31List = new ArrayList<>();
                ads31List.add(ads31);
                apiV31Response.setAds(ads31List);
                apiV31Response.setRequestId(runTimeContext.getRequestId());
                formatAd = ObjectMapperUtils.toJSON(apiV31Response);

            } catch (Exception e) {
                log.error("ApiEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
                throw new SunException(TermCode.SERVER_ERROR, runTimeContext, e);
            }
            BaseResponse.Builder builder = BaseResponse.newBuilder()
                    .setOutputString(formatAd)
                    .setOutputBytes(formatAdBytes == null ? formatAd.getBytes() : formatAdBytes)
                    .setCharset(Charsets.UTF_8)
                    .setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
            builder.setHeader("im-id", runTimeContext.getRequestId());
            BaseResponse baseResponse = builder.build();
            runTimeContext.setBaseResponse(baseResponse);
            return Mono.just(baseResponse);
        } catch (SunException e) {
            return Mono.error(e);
        } catch (Throwable e) {
            log.error("ApiEnvelopWorker error requestId {}", runTimeContext.getRequestId(), e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}