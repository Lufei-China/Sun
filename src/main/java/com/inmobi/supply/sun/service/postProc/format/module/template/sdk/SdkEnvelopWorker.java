package com.inmobi.supply.sun.service.postProc.format.module.template.sdk;

import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.component.model.redis.WidgetModel;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.SecureSession;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.InmobiSecureException;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.WorkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.MathTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.StringWriter;

/**
 * Sdk envelop
 * @author andrew.Ji
 */
@Service

@Slf4j
public class SdkEnvelopWorker implements IEnvelop {
    @Autowired
    VelocityEngine velocityEngine;

    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"SdkEnvelopWorker");
        try {
            if (runTimeContext.getFinalTemplate() == null || runTimeContext.getFinalTemplate().getWidgetSdk() == null) {
                log.error("no template or widget found for placement " + runTimeContext.getPlacementModel().getId());
                //TODO BY REMI,change TermCode
                throw new SunException(TermCode.SERVER_ERROR, runTimeContext);
            }
            WidgetModel widgetModel = runTimeContext.getFinalTemplate().getWidgetSdk();
            log.info("WidgetModel {} requestId {}",widgetModel,runTimeContext.getRequestId());
            final Template template = velocityEngine.getTemplate(widgetModel.getWidgetName(), "UTF-8");
            VelocityContext velocityContext = new VelocityContext();
            MathTool mathTool = new MathTool();
            FormatterModel model = runTimeContext.getFormatterModel();
            velocityContext.put("math", mathTool);
            velocityContext.put("model", model);
            StringWriter stringWriter = new StringWriter();
            template.merge(velocityContext, stringWriter);
            String outputString = StringUtils.trim(stringWriter.toString());
            LogUtils.logInfo(runTimeContext.getRequestId(),"sdk outputString {}",outputString);
            byte[] outputByte = outputString.getBytes(Charsets.UTF_8);
            BaseResponse.Builder builder = BaseResponse.newBuilder();
            builder.setOutputString(outputString)
                    .setOutputBytes(outputByte)
                    .setCharset(Charsets.UTF_8)
                    .setContentType(RuntimeConstant.JSON_CONTENT_UTF8_TYPE);
            if (runTimeContext.isSecureMessageFlag()) {
                try {
                    //should encry sdk byte array response(if isSecureMessageFlag is set)
                    SecureSession session = runTimeContext.getSecureSession();
                    builder.setCharset(null);
                    builder.setContentType(RuntimeConstant.OCTET_STREAM_CONTENT_TYPE);
                    byte[] encryptedOutputByte = session.write(WorkerUtils.compressResponseBytes(outputByte));
                    builder.setOutputBytes(encryptedOutputByte);
                } catch (InvalidMessageException | InmobiSecureException ioe) {
                    log.error("Exception thrown while encrypting responseBytes requestId {}",runTimeContext.getRequestId(), ioe);
                    throw new SunException(TermCode.BAD_SEC_REQ, runTimeContext);
                }
            }
            if (StringUtils.isNotBlank(runTimeContext.getFormatterModel().getEncryptedAdId())) {
                builder.setHeader("IM-v", runTimeContext.getFormatterModel().getEncryptedAdId());
            }
            BaseResponse baseResponse = builder.build();
            runTimeContext.setBaseResponse(baseResponse);
            return Mono.just(baseResponse);
        } catch (SunException e) {
            return Mono.error(e);
        } catch (Throwable e) {
            log.error("SdkEnvelopWorker error requestId {}",runTimeContext.getRequestId(), e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}