package com.inmobi.supply.sun.service.postProc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.*;

import cn.inmobi.pb.api.*;
import cn.inmobi.pb.sun.rr.*;
import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import cn.inmobi.pb.sun.rr.base.User;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.InmobiSecureException;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.response.NoFillResponse;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.IosModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidRequest;
import com.inmobi.supply.sun.utils.*;
import com.supply.adapter.bes.BesBiddingPROTO;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static com.inmobi.supply.component.constant.TermCode.NO_VALID_AD_POOL_RESPONSE;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.RETURN_204;
import static com.inmobi.supply.sun.constant.PerfConstant.SUN_TERM_REASON;
import static com.inmobi.supply.sun.constant.ProtocolConstant.PROTO_BUF;

/**
 * write info to serverHttpResponse
 *
 * @author remi.li
 */
@Slf4j
@Service
public class ResponseWriterFactory extends Worker<BaseResponse, AsyncServletResponse> {
    @Autowired
    private SendKafkaUtils sendKafkaUtils;

    @Autowired
    private PrepareBidRequest prepareBidRequest;
    // 暂时写死可能会更改为ipc的方式
    /*KafkaTopicConfig kafkaTopicConfig = new KafkaTopicConfig("s-sun-trace", "10.112.5.33:9092,10.112.5.34:9092,10.112.5.35:9092,10.112.5.36:9092,10.112.5.37:9092,10.112.5.38:9092");
    @Autowired
    ReactorKafkaProducerHelper kafkaProducerHelper;*/
    @Autowired
    private Map<String, IosModel> iosModelMap;

    @Override
    public Flux<AsyncServletResponse> doProcess(Context context, Flux<BaseResponse> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .flatMap(baseResponse -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "ResponseWriterFactory");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    try {
                        //1. build AsyncServletResponse
                        AsyncServletResponse asyncServletResponse = runTimeContext.getAsyncServletResponse();
                        Charset outputCharset = baseResponse.getCharset();
                        byte[] outputResponseBytes = baseResponse.getOutputBytes();
                        String outputString = baseResponse.getOutputString();
                        log.info("outputString={}, placementId={} pubAccountId={} sourceType={} requestId={}",
                                outputString,
                                runTimeContext.getPlacementModel().getId(),
                                runTimeContext.getAppModel().getPubAccountId(),
                                runTimeContext.getWinBidResponse().getSourceType(),
                                runTimeContext.getRequestId());

                        MediaType contentType = baseResponse.getContentType();
                        asyncServletResponse.setStatus(HttpStatus.OK);
                        asyncServletResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        asyncServletResponse.setHeader("Expires", "-1");
                        asyncServletResponse.setHeader("Pragma", "no-cache");
                        asyncServletResponse.setHeader("X-no-server-unwrapping", "true"); // indicates that the request is not unwrapped
                        if (contentType != null) {
                            asyncServletResponse.setContentType(baseResponse.getContentType());
                        }
                        if (outputCharset != null) {
                            asyncServletResponse.setCharacterEncoding(outputCharset);
                        }
                        Map<String, String> outputHeaders = baseResponse.getHeaderMap();
                        if (outputHeaders != null) {
                            for (Map.Entry<String, String> entry : outputHeaders.entrySet()) {
                                asyncServletResponse.setHeader(entry.getKey(), entry.getValue());
                            }
                        }
                        asyncServletResponse.setResponseBody(outputResponseBytes);
                        asyncServletResponse.setResponseBodyStr(StringUtils.trim(outputString));
                        asyncServletResponse.setPlacementId(runTimeContext.getPlacementId());
                        asyncServletResponse.setTermCode(0);
                        asyncServletResponse.setAdName(String.valueOf(runTimeContext.getAdType().getNumber()));
                        if(runTimeContext.getAppModel().getPubAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())){
                            asyncServletResponse.setBaidu(true);
                        }
                        mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);

                        SunRR.Builder sunRRBuilder = SunRR.getDefaultInstance().toBuilder();
                        //2.build RR data
                        buildSunRR(sunRRBuilder, runTimeContext, null);
                        //3.send RR
                        PerfUtils.perf("sendRR", "status", "valid");

                        int length = sunRRBuilder.build().toByteArray().length;
                        // 暂时去掉 sun request和sun response  减小 sunrr包体
                        sunRRBuilder.setSunRequest(SunRequest.getDefaultInstance());
                        sunRRBuilder.setSunResponse(SunResponse.getDefaultInstance());
                        //log.info("sunRRSizeSuccessLengthBefore:{},sunRRSizeSuccessLengthAfter:{},sunRROriginalLength:{},requestId:{}",length, sunRRBuilder.build().toByteArray().length,sunRRBuilder.getOriginalRequest().toByteArray().length,runTimeContext.getRequestId());
                        //sendKafkaUtils.sendRR(sunRRBuilder.build());
                        List<WinNotice> winNotices = runTimeContext.getWinNoticeList();
                        if (CollectionUtils.isNotEmpty(runTimeContext.getLostNoticeList().entrySet())) {
                            return Flux.fromIterable(runTimeContext.getLostNoticeList().entrySet())
                                    .flatMap(entry -> {
                                        List<WinNotice> lostNotices = entry.getValue();
                                        return Flux.fromIterable(winNotices)
                                                .defaultIfEmpty(WinNotice.getDefaultInstance())
                                                .mergeWith(Flux.fromIterable(lostNotices))
                                                .flatMap(url -> {
                                                    if (!url.equals(WinNotice.getDefaultInstance())) {
                                                        //4.send winNotice & lostNotice
                                                        PerfUtils.perf("sendWinNotice", "dsp", runTimeContext.getWinBidResponse().getSaturnDsp());
                                                        //sendKafkaUtils.sendWinNotice(url);
                                                    }
                                                    return Mono.just(asyncServletResponse);
                                                }).last();
                                    }).last();
                        } else {
                            return Flux.fromIterable(winNotices)
                                    .defaultIfEmpty(WinNotice.getDefaultInstance())
                                    .flatMap(url -> {
                                        if (!url.equals(WinNotice.getDefaultInstance())) {
                                            //4.send winNotice & lostNotice

                                            PerfUtils.perf("sendWinNotice", "dsp", runTimeContext.getWinBidResponse().getSaturnDsp());
                                            //sendKafkaUtils.sendWinNotice(url);
                                        }
                                        return Mono.just(asyncServletResponse);
                                    }).last();
                        }
                    } catch (Exception e) {
//                        log.error("requestId={},ResponseWriterFactory doProcess error", runTimeContext.getRequestId(), e);
                        return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
                    }
                });
    }

    /**
     * 构建原始请求参数
     * @param runTimeContext
     */
    private OriginalRequest buildOriginalRequest(RunTimeContext runTimeContext,TermCode termCode) {
        try{
            OriginalRequest.Builder originalRequestBuilder = runTimeContext.getOriginalRequestBuilder();
            originalRequestBuilder.setRequestId(Optional.ofNullable(runTimeContext.getRequestId()).orElse(""));
            originalRequestBuilder.setPubAccountId((runTimeContext.getAppModel() != null) ? Optional.ofNullable(runTimeContext.getAppModel().getPubAccountId()).orElse("") : "");
            RequestType requestType = runTimeContext.getRequestType();
            if (requestType.isSdk()) {
                originalRequestBuilder.setIntegrationType(IntegrationType.SDK_VALUE);
            } else {
                originalRequestBuilder.setIntegrationType(IntegrationType.API_VALUE);
            }
            if (!runTimeContext.getRequestType().isSdk()) {
                int major = runTimeContext.getRequestType().getVersion().getMajor();
                int minor = runTimeContext.getRequestType().getVersion().getMinor();
                int micro = runTimeContext.getRequestType().getVersion().getMicro();
                originalRequestBuilder.setApiVersion(String.valueOf(major).concat(".").concat(String.valueOf(minor)).concat(".").concat(String.valueOf(micro)));
            } else {
                originalRequestBuilder.setSdkVersion(StringUtils.isNotBlank(runTimeContext.getSdkVersion()) ? runTimeContext.getSdkVersion() : "");
            }
            AppModel appModel = runTimeContext.getAppModel();
            if(!Objects.isNull(appModel)){
                originalRequestBuilder.setAppId(Optional.ofNullable(appModel.getAppId()).orElse(""));
            }
            BaseRequest baseRequest = runTimeContext.getBaseRequest();

            OriginalDevice.Builder builder = OriginalDevice.newBuilder();
            OriginalGeo.Builder originalGeoBuilder = OriginalGeo.newBuilder();

            if(null != runTimeContext.getBaseRequest() && null != runTimeContext.getBaseRequest().getDevice()){
                cn.inmobi.pb.sun.rr.base.Device devicePb = runTimeContext.getBaseRequest().getDevice();
                originalRequestBuilder.setUa(Optional.ofNullable(devicePb.getUa()).orElse(""));
                originalRequestBuilder.setIp(Optional.ofNullable(devicePb.getIp()).orElse(""));
                originalRequestBuilder.setIpv6(Optional.ofNullable(devicePb.getIpv6()).orElse("") );
                if ("ANDROID".equalsIgnoreCase(devicePb.getOs())) {
                    builder.setOaid(Optional.ofNullable(devicePb.getOaid()).orElse(""))
                            .setAndroidIdSha1(Optional.ofNullable(devicePb.getO1()).orElse(""))
                            .setImeiMd5(Optional.ofNullable(devicePb.getMd5Imei()).orElse(""))
                            .setAndroidIdMd5(Optional.ofNullable(devicePb.getUm5()).orElse(""))
                            .setOaidMd5(Optional.ofNullable(devicePb.getOaidMd5()).orElse(""))
                            .setOsType(OsType.ANDROID.getNumber());
                }else{
                    builder.setIdfa(Optional.ofNullable(devicePb.getIfa()).orElse(""))
                            .setIdfaMd5(Optional.ofNullable(devicePb.getIdfaMd5()).orElse(""))
                            .setIdfv(Optional.ofNullable(devicePb.getIdfv()).orElse(""))
                            .setOsType(OsType.IOS.getNumber());
                }

                builder.setOriginalConnType(devicePb.getConnectiontype());
                builder.setDeviceType(devicePb.getType());
                builder.setBootMark(Optional.ofNullable(devicePb.getBootMark()).orElse(""));
                builder.setUpdateMark(Optional.ofNullable(devicePb.getUpdateMark()).orElse(""));

                cn.inmobi.pb.sun.rr.base.Device.Geo geoPb = runTimeContext.getBaseRequest().getDevice().getGeo();
                if (geoPb != null) {
                    originalGeoBuilder.setOriginalLatitude(geoPb.getLat());
                    originalGeoBuilder.setOriginalLongitude(geoPb.getLon());
                }
            }

            if(!Objects.isNull(runTimeContext.getDevice())){
                CaidModel caidModel = runTimeContext.getDevice().getCaidModel();
                if(!Objects.isNull(caidModel)){
                    builder.setPhoneName(Optional.ofNullable(caidModel.getPhoneNameMd5()).orElse(""));
                    builder.setPowerOnTime(Optional.ofNullable(caidModel.getBootTimeSecond()).orElse(""));
                    builder.setDiskSize((long) (Optional.ofNullable(caidModel.getDiskSize()).orElse(0D).doubleValue()));
                    builder.setMemorySize(Optional.ofNullable(caidModel.getMemorySize()).orElse(0D));
                    builder.setDeviceMachine(Optional.ofNullable(caidModel.getDeviceMachine()).orElse(""));
                    builder.setOsUpdateTimeSecond(Optional.ofNullable(caidModel.getOsUpdateTimeSecond()).orElse(""));
                    builder.setCaid(Optional.ofNullable(caidModel.getCaid()).orElse(""));
                    builder.setCaidVersion(Optional.ofNullable(runTimeContext.getDevice().getCaidModel().getCaidVersion()).orElse(""));
                    builder.setPreCaid(Optional.ofNullable(runTimeContext.getDevice().getCaidModel().getPreCaid()).orElse(""));
                    builder.setPreCaidVersion(Optional.ofNullable(runTimeContext.getDevice().getCaidModel().getPreCaidVersion()).orElse(""));
                }
                ConnectionType connectType = runTimeContext.getDevice().getConnectType();
                if(!Objects.isNull(connectType)){
                    builder.setInmobiConnType(runTimeContext.getDevice().getConnectType().getNumber());
                }
                builder.setOrientation(runTimeContext.getDevice().getDeviceOrientation());
                builder.setConnTypeSource(runTimeContext.getDevice().getConnTypeSource());
                builder.setCarrierSource(Optional.of(runTimeContext.getDevice().getCarrierSource()).orElse(0));
                builder.setDpi(Optional.ofNullable(runTimeContext.getDevice().getDeviceDensity()).orElse(0F));


                Carrier carrier = runTimeContext.getDevice().getCarrier();
                if(!Objects.isNull(carrier)){
                    builder.setCarrier(Optional.of(carrier.getNumber()).orElse(0));
                }
            }

            if(null != runTimeContext.getBaseRequest() && null != runTimeContext.getBaseRequest().getDevice()){
                builder.setOriginalModel(runTimeContext.getBaseRequest().getDevice().getModel());
                builder.setOriginalVendor(runTimeContext.getBaseRequest().getDevice().getMake());
                builder.setOriginalWidth(runTimeContext.getBaseRequest().getDevice().getW());
                builder.setOriginalHeight(runTimeContext.getBaseRequest().getDevice().getH());
            }
            builder.setInmobiVendor(Optional.ofNullable(runTimeContext.getInmobiVendor()).orElse(""));
            builder.setInmobiModel(Optional.ofNullable(runTimeContext.getInmobiModel()).orElse(""));
            builder.setInmobiWidth(runTimeContext.getInmobiWidth());
            builder.setInmobiRealHeight(runTimeContext.getInmobiRealHeight());
            //builder.setRomVersion(Optional.of(runTimeContext.getBidRequest().getDevice().getRomVersion()).orElse(""));
            builder.setLmt(runTimeContext.getLat());
            originalRequestBuilder.setDevice(builder.build());

            originalGeoBuilder.setInmobiLatitude(runTimeContext.getInmobiLatitude());
            originalGeoBuilder.setInmobiLongitude(runTimeContext.getInmobiLongitude());
            if(!Objects.isNull(baseRequest)){
                originalGeoBuilder.setOriginalCityCode(Optional.of(baseRequest.getDevice().getGeo().getCity()).orElse(""));
                originalGeoBuilder.setOriginalProvinceCode(Optional.of(baseRequest.getDevice().getGeo().getProvince()).orElse(""));
                originalGeoBuilder.setOriginalDistrictCode(Optional.of(baseRequest.getDevice().getGeo().getDistrict()).orElse(""));
            }


            Geo geo = runTimeContext.getGeo();
            if(!Objects.isNull(geo)){
                originalGeoBuilder.setInmobiCityCode(Optional.of(geo.getCityCode()).orElse(""));
                originalGeoBuilder.setInmobiProvinceCode(Optional.of(geo.getProvinceCode()).orElse(""));
                originalGeoBuilder.setInmobiDistrictCode(Optional.of(geo.getDistrictCode()).orElse(""));
                originalGeoBuilder.setInmobiCaaCityCode(Optional.of(geo.getCaaCityCode()).orElse(""));
                originalGeoBuilder.setInmobiCaaProvinceCode(Optional.of(geo.getCaaProvinceCode()).orElse(""));
            }
            originalRequestBuilder.setGeo(originalGeoBuilder.build());

            OriginalUser.Builder originalUserBuilder = OriginalUser.newBuilder();
            if(!Objects.isNull(baseRequest)){
                User user = baseRequest.getUser();
                String gender = user.getGender();
                if(StringUtils.isNotBlank(gender)){
                    originalUserBuilder.setGender("m".equals(gender)?1:2);
                }else {
                    originalUserBuilder.setGender(0);
                }
                originalUserBuilder.setAge(Optional.of(user.getExt().getAge()).orElse(0));
            }
            originalUserBuilder.setCsid(Optional.ofNullable(runTimeContext.getCsids()).orElse(""));
            originalRequestBuilder.setOriginalUser(originalUserBuilder.build());
            if (null != termCode){
                originalRequestBuilder.setTermCode(termCode.getCode());
            }else {
                originalRequestBuilder.setTermCode(0);
            }
            return originalRequestBuilder.build();
        }catch (Exception e){
            log.error("buildOriginalRequest error!",e);
        }
        return OriginalRequest.getDefaultInstance();
    }

    private void buildSunRR(SunRR.Builder sunRRBuilder, RunTimeContext runTimeContext, TermCode termCode) throws InvalidProtocolBufferException {
        //bidRequest
        if (runTimeContext.getBidRequest() == null || runTimeContext.getBidRequest().equals(BidRequest.getDefaultInstance())) {
            //如果没有bidRequest(在prepareBidRequest之前就出现了error,还是需要统计开发者的请求数目,写bidRequest进入SunRR)
            sunRRBuilder.setBidRequest(prepareBidRequest.buildDefaultBidRequest(runTimeContext));
        } else {
            sunRRBuilder.setBidRequest(runTimeContext.getBidRequest());
        }
//        if (DynamicListConfig.DUCATION_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())) {
//            sunRRBuilder.setTag(1);
//        }
        if (sunRRBuilder.getBidRequest().getDevice().getOsType() == OsType.UNKNOW_OS_TYPE_VALUE && termCode == NO_VALID_AD_POOL_RESPONSE) {
            log.info("unKnow.osType.occur, bidRequest={}", runTimeContext.getBidRequest());
        }
        List<BidResponse> bidResponseList = Lists.newArrayList();
        runTimeContext.getOriginBidResponseList().forEach(bidResponse -> {
            if (CollectionUtils.isNotEmpty(bidResponse.getAdsList())) {
                if (runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType())) != null) {
                    Integer type = runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType()));
                    bidResponseList.add(bidResponse.toBuilder().setAuctionStatus(type).build());
                } else {
                    bidResponseList.add(bidResponse);
                }
            }
        });
        //bidResponse
        sunRRBuilder.addAllBidResponse(bidResponseList);
        //timestamp
        sunRRBuilder.setTimestamp(System.currentTimeMillis());
        //action
        Auction.Builder auctionBuilder = Auction.newBuilder();
        if (runTimeContext.getPlacementModel() != null) {
            auctionBuilder.setPe(Optional.ofNullable(runTimeContext.getPlacementModel().getPubExpectation()).orElse(0L));
            Map<Integer, Double> cosmosRevShare = new HashMap<>();
            runTimeContext.getPlacementModel().getFixRevShareList().forEach(revShare -> cosmosRevShare.put(revShare.getRevShareType(), revShare.getRevShare().doubleValue()));
            auctionBuilder.putAllCosmosRevShare(cosmosRevShare);
        }
        auctionBuilder.setRevenueShareType(Optional.ofNullable(runTimeContext.getRevenueShareType()).orElse(RevenueShareType.UNKNOW_REV_SHARE_TYPE));

        Map<Integer, Long> floor = new HashMap<>();
        runTimeContext.getFloorEntityMap().forEach((floorEntityType, value) -> floor.put(floorEntityType.getNumber(), value));
        auctionBuilder.putAllFloor(floor);

        Map<Integer, Long> bidding = new HashMap<>();
        runTimeContext.getBidding().forEach((floorEntityType, value) -> bidding.put(floorEntityType.getNumber(), value));
        auctionBuilder.putAllBidding(bidding);

        if (runTimeContext.getWinBidResponse() != null) {
            auctionBuilder.setWinner(SourceType.forNumber(runTimeContext.getWinBidResponse().getSourceType()));
        } else {
            auctionBuilder.setWinner(SourceType.UNKONW_SOURCE_TYPE);
        }

        auctionBuilder.setChargeAdv(runTimeContext.getChargeAndPayMap().getOrDefault(ChargeAndPay.CHARGE, 0L));
        auctionBuilder.setPayPub(runTimeContext.getChargeAndPayMap().getOrDefault(ChargeAndPay.PAY, 0L));
        auctionBuilder.setFirstPrice(Optional.ofNullable(runTimeContext.getFirstPrice()).orElse(0L));
        auctionBuilder.setSecondPrice(Optional.ofNullable(runTimeContext.getSecondPrice()).orElse(0L));
        auctionBuilder.addAllVb(Optional.ofNullable(runTimeContext.getVirtualBids()).orElse(new ArrayList<>()));
        sunRRBuilder.setAution(auctionBuilder);
        //termCode
        if (termCode != null) {
            sunRRBuilder.setTermCode(termCode.getCode());
        } else {
            sunRRBuilder.setTermCode(0);
        }
        //requestId
        sunRRBuilder.setRequestId(Optional.ofNullable(runTimeContext.getRequestId()).orElse(""));
        //add sun request
        if (runTimeContext.getRequestType() == RequestType.SDK) {
            sunRRBuilder.setSunRequest(SunRequest.newBuilder().putAllSdkRequest(runTimeContext.getSdkRequest()).build());
        } else {
            // tode v3 -> base
            //sunRRBuilder.setSunRequest(SunRequest.newBuilder().setApiV3Request(Optional.ofNullable(runTimeContext.getApiv3Request()).orElse(ApiV3Request.getDefaultInstance())).build());
            BaseRequest baseRequest = runTimeContext.getBaseRequest();
            ApiV3Request.Builder  apiv3RequestBuilder= ApiV3Request.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(JsonFormat.printer().print(baseRequest), apiv3RequestBuilder);
            sunRRBuilder.setSunRequest(SunRequest.newBuilder().setApiV3Request(apiv3RequestBuilder.build()).build());

        }

        // build ad filter view  创建adpool流量漏斗
        if (runTimeContext.getWinAd() != null) {
            //  如果是Saturn key: {dsp_account_id}_{segment_id}, 如果是Perf key: {dsp_account_id}_{campaign_id}_{segment_id};
            Ad winad = runTimeContext.getWinAd();
            if (StringUtils.isNotBlank(winad.getSaturnChannelId()) && StringUtils.isNotBlank(winad.getSaturnSegmentId())) {
                String str = winad.getSaturnChannelId() + "_" + winad.getSaturnSegmentId();
                sunRRBuilder.setWinner(str);
            }
            if (StringUtils.isBlank(winad.getSaturnChannelId())) {
                String str = winad.getAdvAccId() + "_" + winad.getCampId() + "_" + winad.getGroupId();
                sunRRBuilder.setWinner(str);
            }
        }
        if (runTimeContext.getBiddingAfterFilter() != null) {
            sunRRBuilder.putAllBiddingAfterFilter(runTimeContext.getBiddingAfterFilter());
        }
        //add winResponse
        sunRRBuilder.setWinBidResponse(Optional.ofNullable(runTimeContext.getWinBidResponse()).orElse(BidResponse.getDefaultInstance()));

        //添加原始请求
        OriginalRequest originalRequest = buildOriginalRequest(runTimeContext,termCode);
        sunRRBuilder.setOriginalRequest(originalRequest);

        try{
            //add adx info
            sunRRBuilder.setAccountType(runTimeContext.getAccountType());
            RequestAdapterResponse requestAdapterResponse = runTimeContext.getRequestAdapterResponse();
            if(null != requestAdapterResponse){
                JsonObject publisherSpecificFields = requestAdapterResponse.getPublisherSpecificFields();
                if(!Objects.isNull(publisherSpecificFields)){
                    String adxAppId = Objects.isNull(publisherSpecificFields.get("adxAppId"))?"": publisherSpecificFields.get("adxAppId").getAsString();
                    String adxSlotId = Objects.isNull(publisherSpecificFields.get("adxSlotId"))?"": publisherSpecificFields.get("adxSlotId").getAsString();
                    sunRRBuilder.setAdxAppid(adxAppId);
                    sunRRBuilder.setAdxSlotid(adxSlotId.replaceAll(" ",""));
                }
            }
        }catch (Exception e){
            log.error("add adx sunRR info error: {}", e);
        }
        //sendKafka(runTimeContext, termCode);
        log.info("requestId {} sunRR  {} termCode {}", runTimeContext.getRequestId(), sunRRBuilder, termCode);
    }

    @Override
    public void processIfError(RunTimeContext runTimeContext, SunException sunException, Map<Integer, RunTimeContext> newRunTimeTaskNorm) {
        TermCode termCode = sunException.getTermCode();
        runTimeContext.getTermCodes().add(termCode);
        if (termCode.equals(TermCode.SERVER_ERROR)) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            sunException.printStackTrace(writer);
            StringBuffer buffer = stringWriter.getBuffer();
//            log.error("sunException={} placementId={}", buffer.toString(), Optional.ofNullable(runTimeContext.getPlacementId()).orElse(0L));
        }
        PerfUtils.perf(SUN_TERM_REASON, Tag.of("term", termCode.getDescription()));
        if (runTimeContext.getAppModel() != null && runTimeContext.getAppModel().getPubAccountId() != null && DynamicListConfig.ADAPTER_BAIDU_LIST.get().contains(runTimeContext.getAppModel().getPubAccountId())) {
            PerfUtils.perf("debugBaidu", Tag.of("term", termCode.getDescription()));
        }
        //1. 不管流程是否异常，都要发RR日志
        SunRR.Builder sunRRBuilder = SunRR.getDefaultInstance().toBuilder();
        try {
            buildSunRR(sunRRBuilder, runTimeContext, termCode);
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"buildSunRR error {}",e);
        }
        if (termCode.isNeedSendRR()) {
            PerfUtils.perf("sendRR", "status", "invalid");

            int length = sunRRBuilder.build().toByteArray().length;
            // 暂时去掉 sun request和sun response  减小 sunrr包体
            sunRRBuilder.setSunRequest(SunRequest.getDefaultInstance());
            sunRRBuilder.setSunResponse(SunResponse.getDefaultInstance());
            //log.info("sunRRSizeErrorBeforeLength:{},sunRRSizeErrorAfterLength:{},requestId:{}",length, sunRRBuilder.build().toByteArray().length,runTimeContext.getRequestId());

            //sendKafkaUtils.sendRR(sunRRBuilder.build());
        }
        //2. 不管流程是否异常，都要构造最终的serverHttpResponse
        AsyncServletResponse asyncServletResponse = Optional.ofNullable(runTimeContext.getAsyncServletResponse()).orElse(new AsyncServletResponse());
        if (runTimeContext.getPlacementId() != null) {
            asyncServletResponse.setPlacementId(runTimeContext.getPlacementId());
        }
        asyncServletResponse.setTermCode(termCode.getCode());
        AdType adType = runTimeContext.getAdType();
        if(!Objects.isNull(adType)){
            asyncServletResponse.setAdName(String.valueOf(adType.getNumber()));
        }
        if(!Objects.isNull(runTimeContext.getAppModel()) && runTimeContext.getAppModel().getPubAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())){
            asyncServletResponse.setBaidu(true);
        }
        MediaType mediaType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.APPLICATION_JSON);
        String pubAccountId = (runTimeContext.getAppModel() != null) ? Optional.ofNullable(runTimeContext.getAppModel().getPubAccountId()).orElse("") : "";
        RequestType requestType = Optional.ofNullable(runTimeContext.getRequestType()).orElse(RequestType.IN_VALID);
        if (ProtocolConstant.getProtocolBuyContentType(mediaType) == PROTO_BUF
                || (RETURN_204.get().contains(pubAccountId) && requestType == RequestType.CUSTOM_API)) {
            //pb请求返回流程 body统一返回为空
            if (termCode.isIfOuterError()) {
                asyncServletResponse.setStatus(HttpStatus.BAD_REQUEST);
            } else {
                asyncServletResponse.setStatus(HttpStatus.NO_CONTENT);
            }
            asyncServletResponse.setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
            asyncServletResponse.setCharacterEncoding(Charsets.UTF_8);
            newRunTimeTaskNorm.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
            if (pubAccountId.equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                BesBiddingPROTO.BidResponse.Builder builder = BesBiddingPROTO.BidResponse.newBuilder();
                String requestId = "";
                if (runTimeContext.getRequestAdapterResponse() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields() != null && runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("requestId") != null) {
                    requestId = runTimeContext.getRequestAdapterResponse().getPublisherSpecificFields().get("requestId").getAsString();
                }
                builder.setId(requestId);
                asyncServletResponse.setStatus(HttpStatus.OK);
                asyncServletResponse.setResponseBody(builder.build().toByteArray());
            }
            return;
        }

        if (termCode.isIfOuterError()) {
            //Outer error
            asyncServletResponse.setStatus(HttpStatus.BAD_REQUEST);
        } else {
            //Inner error
            asyncServletResponse.setStatus(HttpStatus.OK);
        }
        boolean isTraceEnable = SymbolString.TRUE.equalsIgnoreCase(runTimeContext.getHeaders().get(StringUtils.lowerCase(RequestHeader.TRACE_ENABLED_HEADER)));
        if (StringUtils.isBlank(runTimeContext.getRequestId())) {
            asyncServletResponse.setCharacterEncoding(Charsets.UTF_8);
            asyncServletResponse.setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
            asyncServletResponse.setResponseBody(NoFillResponse.jsonNoFillResponse());
            if (isTraceEnable) {
                asyncServletResponse.setResponseBody(NoFillResponse.jsonNoFillResponseNoRequestIdWithTermReason(termCode.getDescription()));
            }
        } else {
            byte[] noFillResponse = NoFillResponse.jsonNoFillResponse(runTimeContext.getRequestId());
            if (isTraceEnable) {
                noFillResponse = NoFillResponse.jsonNoFillResponseWithTermReason(runTimeContext.getRequestId(), termCode.getDescription());
            }
            if (runTimeContext.isSecureMessageFlag()) {
                try {
                    //should encry sdk byte array response(if isSecureMessageFlag is set)
                    SecureSession session = runTimeContext.getSecureSession();
                    asyncServletResponse.setCharacterEncoding(null);
                    asyncServletResponse.setContentType(RuntimeConstant.OCTET_STREAM_CONTENT_TYPE);
                    byte[] encryptedOutputByte = session.write(WorkerUtils.compressResponseBytes(noFillResponse));
                    asyncServletResponse.setResponseBody(encryptedOutputByte);
                } catch (InvalidMessageException | InmobiSecureException ioe) {
//                    log.error("Exception thrown while encrypting no fill responseBytes", ioe);
                    asyncServletResponse.setResponseBody(noFillResponse);
                }
            } else {
                asyncServletResponse.setCharacterEncoding(Charsets.UTF_8);
                asyncServletResponse.setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
                asyncServletResponse.setResponseBody(noFillResponse);
            }
        }
        newRunTimeTaskNorm.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
    }

    /*void sendKafka(RunTimeContext runTimeContext, TermCode termCode) {
        if (!DynamicBooleanConfig.MQ_SEND.get()) {
            return;
        }

        try {
            SunRunTimeTrace sunRunTimeTrace = new SunRunTimeTrace();
            sunRunTimeTrace.setPlacementId(runTimeContext.getPlacementId());
            sunRunTimeTrace.setRequestId(runTimeContext.getRequestId());
            sunRunTimeTrace.setUsedTime(System.currentTimeMillis()-runTimeContext.getStartTime());
            sunRunTimeTrace.setBundle(runTimeContext.getRequestBundleId());
            if (runTimeContext.getPlacementModel() != null) {
                sunRunTimeTrace.setPe(runTimeContext.getPlacementModel().getPubExpectation());
            }
            if (termCode == null) {
                sunRunTimeTrace.setTermCode(0);
            } else {
                sunRunTimeTrace.setTermCode(termCode.getCode());
            }
            if (CollectionUtils.isNotEmpty(runTimeContext.getOriginBidResponseList())) {
                runTimeContext.getOriginBidResponseList().forEach(bidResponse -> {
                    if (CollectionUtils.isEmpty(bidResponse.getAdsList())) {
                        return;
                    }
                    Ad ad = bidResponse.getAdsList().get(0);
                    if (StringUtils.isBlank(ad.getSaturnChannelId())) {
                        sunRunTimeTrace.setDemondGroupId(ad.getGroupId());
                        sunRunTimeTrace.setDemondCampId(ad.getCampId());
                        sunRunTimeTrace.setDemondBidPrice(ad.getBidPrice());
                        if (runTimeContext.getDspMap() != null && runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType())) != null) {
                            Integer type = runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType()));
                            sunRunTimeTrace.setDemondAuctionStatus(type);
                        }
                        sunRunTimeTrace.setDemondCpcPrice(ad.getBidPriceCpc());
                        sunRunTimeTrace.setDemondAdId(ad.getAdId());
                    sunRunTimeTrace.setDemondAccId(ad.getAdvAccId());
                        sunRunTimeTrace.setDemondBidType(ad.getBidType());
                        sunRunTimeTrace.setDemondNetDeduction(String.valueOf(ad.getNetDeduction()));
                    }
                    if (StringUtils.isNotBlank(ad.getSaturnChannelId())) {
                        sunRunTimeTrace.setSaturnSegmentId(ad.getSaturnSegmentId());
                        sunRunTimeTrace.setSaturnChannelId(ad.getSaturnChannelId());
                        sunRunTimeTrace.setSaturnBidPrice(ad.getBidPrice());
                        sunRunTimeTrace.setSaturnNetDeduction(String.valueOf(ad.getNetDeduction()));
                        sunRunTimeTrace.setSaturnBidType(ad.getBidType());
                        if (runTimeContext.getDspMap() != null && runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType())) != null) {
                            Integer type = runTimeContext.getDspMap().get(String.valueOf(bidResponse.getSourceType()));
                            sunRunTimeTrace.setSaturnAuctionStatus(type);
                        }
                    }
                });
            }
            if (runTimeContext.getWinBidResponse() != null) {
                sunRunTimeTrace.setWinner(SourceType.forNumber(runTimeContext.getWinBidResponse().getSourceType()).getNumber());
            }
            if (runTimeContext.getWinAd() != null) {
                sunRunTimeTrace.setRrAdpoolDeduction(String.valueOf(runTimeContext.getWinAd().getAdpoolDeduction()));
                sunRunTimeTrace.setRrNetDeduction(String.valueOf(runTimeContext.getWinAd().getNetDeduction()));
                sunRunTimeTrace.setRrSettleType(Optional.ofNullable(runTimeContext.getWinAd().getSettleType()).orElse(0));
            }
            sunRunTimeTrace.setRenderType(runTimeContext.getAdType().getNumber());
            sunRunTimeTrace.setRrRevenueShareType(runTimeContext.getPlacementModel().getRevShareType());
            sunRunTimeTrace.setFloor(runTimeContext.getBaiduBrice());
            sunRunTimeTrace.setRrFirstPrice(Optional.ofNullable(runTimeContext.getFirstPrice()).orElse(0L));
            sunRunTimeTrace.setRrSecodnPrice(Optional.ofNullable(runTimeContext.getSecondPrice()).orElse(0L));
            if (runTimeContext.getChargeAndPayMap() != null) {
                sunRunTimeTrace.setRrPay(runTimeContext.getChargeAndPayMap().getOrDefault(ChargeAndPay.PAY, 0L));
                sunRunTimeTrace.setRrCharge(runTimeContext.getChargeAndPayMap().getOrDefault(ChargeAndPay.CHARGE, 0L));
            }
            if (runTimeContext.getAppModel() != null) {
                sunRunTimeTrace.setPubAccount(runTimeContext.getAppModel().getPubAccountId());
            }
            sunRunTimeTrace.setTimestamp(System.currentTimeMillis());
            kafkaProducerHelper.send(kafkaTopicConfig, JSON.toJSONString(sunRunTimeTrace));
            log.info("sendkafka {} requestId {}", JSON.toJSONString(sunRunTimeTrace), runTimeContext.getRequestId());
            PerfUtils.perf("sun.sendkafka");
        } catch (Exception e) {
//            log.error("SunRunTimeTrace error  sendkafka {} ", JSON.toJSONString(sunRunTimeTrace), e);
        }
    }*/

    @Override
    public Worker getNextProc() {
        //最后一个阶段喽
        return null;
    }
}