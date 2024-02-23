package com.inmobi.supply.sun.service.postProc.format.module.template.sdk;

import cn.inmobi.pb.api.OsType;
import com.google.common.base.Charsets;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.InmobiSecureException;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.service.postProc.format.module.template.TemplateTool;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.AdUnitContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.CreativeContext;
import com.inmobi.supply.sun.service.postProc.format.module.template.models.StaticHtmlContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.SecureSession;
import com.inmobi.supply.sun.utils.VastUtil;
import com.inmobi.supply.sun.utils.WorkerUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.AD_CONTEXT;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.FIRST_AD_CONTEXT;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.FORMATTER_MODEL;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.HTML_CONTEXT;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.REWARDED_VIDEO_HTML_RESPONSE_TEMPLATE;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.STATIC_HTML_RESPONSE_TEMPLATE;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.TEMPLATE_TOOL;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.buildHtmlContext;
import static com.inmobi.supply.sun.service.postProc.format.module.template.HtmlContextUtil.buildHtmlContextVideo;


import com.jayway.jsonpath.spi.JsonProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SdkHtmlEnvelopWorker implements IEnvelop {

    @Autowired
    VelocityEngine velocityEngine;

    @Autowired
    VastUtil vastUtil;

    @Override
    public Mono<BaseResponse> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"SdkHtmlEnvelopWorker");
        try {
            if (runTimeContext.getFinalTemplate() == null
                    || runTimeContext.getFinalTemplate().getWidgetSdk() == null) {
                log.error("no template or widget found for placement " + runTimeContext.getPlacementModel()
                        .getId());
                throw new SunException(TermCode.SERVER_ERROR, runTimeContext);
            }

            VelocityContext velocityContext = new VelocityContext();
            FormatterModel model = runTimeContext.getFormatterModel();
            StaticHtmlContext htmlContext = null;
            AdUnitContext adUnitContext = null;
            Template template = null;

            if (runTimeContext.getFormatterModel().getImage() != null) {
                template = velocityEngine.getTemplate(STATIC_HTML_RESPONSE_TEMPLATE, "UTF-8");
                htmlContext = buildHtmlContext(runTimeContext, model);
                // fix error
                htmlContext.setAdHeight(model.getImage().getHeight());
                htmlContext.setAdWidth(model.getImage().getWidth());
                velocityContext.put(HTML_CONTEXT, htmlContext);
            } else if (runTimeContext.getFormatterModel().getVideo() != null) {
                template = velocityEngine.getTemplate(REWARDED_VIDEO_HTML_RESPONSE_TEMPLATE, "UTF-8");
                htmlContext = buildHtmlContextVideo(runTimeContext, model);
                velocityContext.put(HTML_CONTEXT, htmlContext);
                adUnitContext = getAdUnitContext(runTimeContext, model);
                velocityContext.put(AD_CONTEXT, adUnitContext);
                List<CreativeContext> creativeContextList = adUnitContext.getCreativeList();
                if (!creativeContextList.isEmpty()) {
                    velocityContext.put(FIRST_AD_CONTEXT, adUnitContext.getCreativeList().get(0));
                }
            }

            velocityContext.put(TEMPLATE_TOOL, new TemplateTool());
            velocityContext.put(FORMATTER_MODEL, model);

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
                    log.error("Exception thrown while encrypting responseBytes", ioe);
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
            LogUtils.logError(runTimeContext.getRequestId(),"SdkHtmlEnvelopWorker error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private AdUnitContext getAdUnitContext(RunTimeContext runTimeContext, FormatterModel model) throws JSONException {
        String prefix = "";

        Resource resource = new ClassPathResource("prefix.html");
        InputStream inputStream = null;
        try {
            inputStream = resource.getInputStream();
            byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
            prefix = new String(bdata, StandardCharsets.UTF_8);
        } catch (IOException e) {
            prefix = "Error in HTML prefix";
            log.error("Prefix not found");
        }
        String mRaidPath =
                OsType.IOS == model.getOsType()
                        ? "https://cdn-supply.inmobi.cn/sdk/sdk/700/ios/mraid.js"
                        : "https://cdn-supply.inmobi.cn/sdk/sdk/700/android/mraid.js";

        prefix = StringUtils.replace(prefix, "${ad.mraidPath}", mRaidPath);





        ArrayList<CreativeContext> creativeList = new ArrayList();
        String vastXmlString = vastUtil.generateVastXmlString(runTimeContext, model);
        Object jsonObjectCreativeCtxObject = getCreativeCtxObject(vastXmlString);

        final Set<Short> viewablePartners = new HashSet<>();
        viewablePartners.add((short) 2);
        viewablePartners.add((short) 3);
        CreativeContext creativeContext =
                CreativeContext.builder()
                        .adImpressionId(runTimeContext.getFormatterModel().getRequestId())
                        .bannerUrl(null)
                        .altText(null)
                        .ns("im_6370_")
                        .linkUrl(null)
                        .supportsCreativeApi(true)
                        .onTouchStartAction("handleTouchStart()")
                        .onTouchEndAction("handleTouchEnd()")
                        .onClickAction(null)
                        .prefix(prefix)
                        .suffix("</body>\n</html>\n")
                        .target("_top")
                        .thirdPartyHtml(null)
                        //TODO put height width for video
//            .width(640)
//            .height(360)
                        .metricsEnabled(false)
                        .firesBeaconUrl(false)
                        .firesTpit(false)
                        .trackingCodes(null)
                        .jsonObject(jsonObjectCreativeCtxObject)
                        .openingLandingUrl(runTimeContext.getFormatterModel().getLandingUrl())
                        .cauElementJsonObject(null)
                        .glanceOpenMethod(0)
                        .appInstall(false)
                        .fallbackUrl(model.getFallbackUrl())
                        .landingPage(model.getLandingUrl())
                        .viewabilityPartnerListToEnableJs(viewablePartners)
                        .nativeDeeplinkLandingUrl(null)
                        .omidEnabledOnAPI(false)
                        .adChoicesUrl(null)
                        .videoExperience("2")
                        .beaconUrl(runTimeContext.getAdRunTimeInfo().getBeaconUrl())
                        .clickServerUrl("")
                        .build();

        creativeList.add(creativeContext);
        Object sitePreferencesJsonObject = getSitePreferencesJsonObject();

        AdUnitContext adUnitContext =
                AdUnitContext.builder()
                        .sdkVersion("i917")
                        .supplyWidth(model.getDeviceScreenWidth())
                        .supplyHeight(model.getDeviceScreenHeight())
                        .displayDaaWidget(false)
                        .secure(false)
                        .maxPodDuration(90901)
                        .prefix(prefix)
                        .suffix("</body>\\n</html>\\n")
                        .incentiveJsonObject(new JSONObject())
                        .sitePreferencesJson(sitePreferencesJsonObject)
                        .creativeList(creativeList)
                        .closeButtonSize((short) 60)
                        .ctrFactor((short) 1)
                        //TODO revert rewarded
                        .rewarded(runTimeContext.getPlacementModel().getType()== 4 ? true : false)
                        .adChoicesUrl("https://www.inmobi.com/ad-opt-out?i=%7B%7D")
                        .adExpiryTime(1634546798)
                        .isVideo(true)
                        .iPhoneX(false)
                        .safeAreaSDK(false)
                        .vpaidDisabledByPublisher(false)
                        .skanMeta(null)
                        .sdkAdPodRequest(false)
                        .build();
        return adUnitContext;
    }

    private Object getSitePreferencesJsonObject() throws JSONException {
        JSONObject sitePreferencesJson = new JSONObject();
        sitePreferencesJson.put("video",  new JSONObject()
                .put("skipDelay","2")
                .put("soundOn", false)
                .put("skippable", true)
                .put("showCloseDuringPlay", true)
                .put("preBuffer", "WIFI")
                .put("autoCloseSettings", new JSONObject()
                        .put("duration", 10)
                        .put("autoClose", false)));

        Object sitePreferencesJsonObject = JsonProviderFactory.createProvider()
                .parse(sitePreferencesJson.toString());
        return sitePreferencesJsonObject;
    }

    private Object getCreativeCtxObject(String vastXmlString) throws JSONException {
        JSONObject jsonObjectCreativeCtx = new JSONObject();
        jsonObjectCreativeCtx.put("videoExperience","1")
                .put("omidEnabled",true)
                .put("rvExpEnabled", true)
                .put("rvExpScale", "50.5")
                .put("vastVideoOrientation", "landscape")
                .put("video",new JSONObject()
                        .put("vast",new JSONObject()
                                .put("width", 16)
                                .put("vastXml", vastXmlString)
                                .put("version", "2.0")
                                .put("height",9)));

        Object jsonObjectCreativeCtxObject = JsonProviderFactory.createProvider()
                .parse(jsonObjectCreativeCtx.toString());
        return jsonObjectCreativeCtxObject;
    }


}

