package com.inmobi.supply.sun.service.postProc.format.module.template.api;

import static com.inmobi.supply.sun.constant.RequestType.API_ORTB_1_0;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.FORMATTER_MODEL;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.HTML_CONTEXT;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.STATIC_HTML_TEMPLATE;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.TEMPLATE_TOOL;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.buildHtmlContext;

import cn.inmobi.pb.api.MaterialMeta;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.component.model.rr.ApiOrtbResponse;
import com.inmobi.supply.component.model.rr.ApiOrtbResponse.Bid;
import com.inmobi.supply.component.model.rr.ApiOrtbResponse.SeatBid;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.ApiOrtbContext;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.service.postProc.format.module.template.TemplateTool;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.StaticHtmlContext;
import java.io.StringWriter;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ApiHtmlEnvelopWorker implements IEnvelop {

  @Autowired
  VelocityEngine velocityEngine;

  @Override
  public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
    try {
      FormatterModel formatterModel = runTimeContext.getFormatterModel();
      RequestType requestType = runTimeContext.getRequestType();
      String formatAd = "";

      final Template template = velocityEngine.getTemplate(STATIC_HTML_TEMPLATE,
              "UTF-8");
      VelocityContext velocityContext = new VelocityContext();

      StaticHtmlContext htmlContext = buildHtmlContext(runTimeContext, formatterModel);

      velocityContext.put(TEMPLATE_TOOL, new TemplateTool());
      velocityContext.put(FORMATTER_MODEL, formatterModel);
      velocityContext.put(HTML_CONTEXT, htmlContext);

      StringWriter stringWriter = new StringWriter();
      template.merge(velocityContext, stringWriter);
      String formattedAdContent = StringUtils.trim(stringWriter.toString());

      log.info("api outputString={}", formattedAdContent);

      try {
        if(requestType == API_ORTB_1_0) {
          ApiOrtbContext apiContext = (ApiOrtbContext) runTimeContext.getApiContext();
          MaterialMeta creative = runTimeContext.getWinBidResponse().getAds(0).getCreative();
          ApiOrtbResponse apiOrtbResponse = ApiOrtbResponse.builder()
                  .cur("USD")
                  .id(formatterModel.getRequestId())
                  .seatBid(SeatBid.builder()
                          .bid(Arrays.asList(Bid.builder()
                                  .nurl(creative.getWinNoticeUrlCount() != 0
                                          ? creative.getWinNoticeUrl(0)
                                          : null)
//                                        .lurl
                                  .price(runTimeContext.getWinAd().getBidPrice())
                                  .crid(runTimeContext.getWinBidResponse().getAdsList().get(0).getCreative().getMaterialId())
                                  .impid(apiContext.getApiOrtbRequest().getImp(0).getId())
//                                        .adomain()
//                                        .cat(runTimeContext.getWinBidResponse().getAdsList().get(0).getCreative().)
                                  .adm(formattedAdContent)
                                  .build()))
                          .build())
                  .build();
          //runTimeContext.setApiOrtbResponse(apiOrtbResponse);
          formatAd = ObjectMapperUtils.toJSON(apiOrtbResponse);
        }
      } catch (Exception e) {
        log.error("requestId {} ApiEnvelopWorker error", runTimeContext.getRequestId(), e);
        throw new SunException(TermCode.SERVER_ERROR, runTimeContext, e);
      }
      BaseResponse.Builder builder = BaseResponse.newBuilder()
              .setOutputString(formatAd)
              .setOutputBytes(formatAd.getBytes())
              .setCharset(Charsets.UTF_8)
              .setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
      builder.setHeader("im-id", runTimeContext.getRequestId());
      BaseResponse baseResponse = builder.build();
      runTimeContext.setBaseResponse(baseResponse);
      return Mono.just(baseResponse);
    } catch (SunException e) {
      return Mono.error(e);
    } catch (Throwable e) {
      log.error("requestId {} ApiEnvelopWorker error", runTimeContext.getRequestId(), e);
      return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
    }
  }
}
