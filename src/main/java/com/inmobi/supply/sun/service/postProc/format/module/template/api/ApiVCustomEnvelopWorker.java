package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import cn.inmobi.pb.api.Ad;
import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.saturn.custom.BidMethod;
import com.google.common.base.Charsets;
import com.inmobi.supply.adapterlibrary.entity.CustomEnvelopingResponse;
import com.inmobi.supply.adapterlibrary.entity.ResponseExt;
import com.inmobi.supply.adapterlibrary.response.api.ResponseEnvelopingIdentifier;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.StaticHtmlContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.EscapeTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.StringWriter;
import java.math.BigDecimal;

import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.BANNER_H5_HTML;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.buildHtmlContext;

/**
 * Api3.1 && Api3.3 envelop
 *
 * @author andrew.Ji
 */
@Service
@Slf4j
public class ApiVCustomEnvelopWorker implements IEnvelop {
    @Autowired
    private ResponseEnvelopingIdentifier responseEnvelopingIdentifier;

    @Autowired
    VelocityEngine velocityEngine;

    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        try {
            FormatterModel formatterModel = runTimeContext.getFormatterModel();
            String formatAd;
            byte[] formatAdBytes;
            try {
                ResponseExt.ResponseExtBuilder extBuilder = ResponseExt.builder()
                        .bundle(runTimeContext.getBaseRequest().getApp().getBundle())
                        .creativeId(runTimeContext.getWinAd().getAdId())
                        .fixedPEAmount(runTimeContext.getWinAd().getBidPrice())
                        .publisherSpecificFields(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields())
                        .advertiserId(String.valueOf(runTimeContext.getWinAd().getAdvAccId()))
                        .landingPage(formatterModel.getTargetUrl())
                        .openExternal("DEEPLINK".equalsIgnoreCase(formatterModel.getOpenMode()))
                        .isApp(formatterModel.isApp())
                        .packageName(runTimeContext.getBaseRequest().getApp().getName())
                        .expirationTime(9000);
                ResponseExt ext = null;
                if (formatterModel.getCreativeType() == CreativeType.IMAGE
                        || formatterModel.getCreativeType() == CreativeType.GIF) {
                    ext = extBuilder.publisherCreativeUrl(formatterModel.getImage().getUrl()).build();
                }
                if (formatterModel.getCreativeType() == CreativeType.VIDEO) {
                    ext = extBuilder.publisherCreativeUrl(formatterModel.getVideo().getVideoUrl()).build();
                }
                long bid = runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY).longValue();
                Ad winAd = runTimeContext.getWinAd();
                int bidType = winAd.getBidType();
                if (winAd.getBidPriceCpc() > 0L) {
                    PerfUtils.perf("sun.bid.type", Tag.of("type", String.valueOf(BidMethod.CPC_VALUE)));
                    formatterModel.setCpcPrice(bid);
                    formatterModel.setBidMethod(2);
                } else if (bidType == BidMethod.OCPC_VALUE){
                    PerfUtils.perf("sun.bid.type", Tag.of("type", String.valueOf(BidMethod.OCPC_VALUE)));
                    formatterModel.setOcpmPrice(bid);
                    formatterModel.setBidMethod(3);
                    formatterModel.setGroupId(String.valueOf(winAd.getGroupId()));
                } else {
                    PerfUtils.perf("sun.bid.type", Tag.of("type", String.valueOf(BidMethod.CPM_VALUE)));
                    formatterModel.setEcpmPrice(bid);
                    formatterModel.setBidMethod(1);
                }
                formatterModel.setCreativeType(CreativeType.forNumber(runTimeContext.getWinAd().getCreative().getCreativeType()));
                formatterModel.setPublisherSpecificFields(runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields());
                // 对特定广告位支持模板渲染
                if (DynamicListConfig.ORTB_PUBLISHER_LIST.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                    //价格进行人民币美元换算
                    BigDecimal usdCny = new BigDecimal(DynamicStringConfig.USD_TO_CNY.get());
                    //人民币换成元
                    BigDecimal yuan = new BigDecimal(bid).divide(new BigDecimal("100"), 4, BigDecimal.ROUND_HALF_UP);
                    //元换成美元
                    formatterModel.setBid(yuan.divide(usdCny, 4, BigDecimal.ROUND_HALF_UP).toString());
                    LogUtils.logInfo(runTimeContext.getRequestId(),"priceToCny unit {} cny {}, usd {}",usdCny,yuan,formatterModel.getBid());
                    Template template = velocityEngine.getTemplate(BANNER_H5_HTML, "UTF-8");
                    VelocityContext velocityContext = new VelocityContext();
                    EscapeTool escapeTool = new EscapeTool();
                    velocityContext.put("tool",escapeTool);
                    velocityContext.put("model", formatterModel);
                    StaticHtmlContext context = buildHtmlContext(runTimeContext, formatterModel);;
                    context.setAdHeight(formatterModel.getImage().getHeight());
                    context.setAdWidth(formatterModel.getImage().getWidth());
                    velocityContext.put("htmlContext", context);
                    StringWriter stringWriter = new StringWriter();
                    template.merge(velocityContext,stringWriter);
                    formatterModel.setAdm(stringWriter.toString());
                }

                CustomEnvelopingResponse customEnvelopingResponse = responseEnvelopingIdentifier.normalize(runTimeContext.getRequestAdapterResponse().getMkVersion(), formatterModel, ext, runTimeContext.getTraceMarker());
                if (customEnvelopingResponse == null || ArrayUtils.isEmpty(customEnvelopingResponse.getOutputBytes())) {
                    throw new SunException(TermCode.API_ENVELOP_ERROR, runTimeContext);
                }
                formatAd = new String(customEnvelopingResponse.getOutputBytes(), Charsets.UTF_8);
                formatAdBytes = customEnvelopingResponse.getOutputBytes();
                log.info("CUSTOM_API formatterModel: {}, customEnvelopingResponse:{} ", formatterModel, formatAd);
            } catch (SunException e) {
                throw new SunException(TermCode.API_ENVELOP_ERROR, runTimeContext);
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