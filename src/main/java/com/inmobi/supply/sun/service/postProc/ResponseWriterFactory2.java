package com.inmobi.supply.sun.service.postProc;

import cn.inmobi.pb.api.*;
import cn.inmobi.pb.sun.rr.*;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.InmobiSecureException;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.model.response.NoFillResponse;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.IosModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidRequest;
import com.inmobi.supply.sun.utils.*;
import com.supply.adapter.bes.BesBiddingPROTO;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import static com.inmobi.supply.component.constant.TermCode.NO_VALID_AD_POOL_RESPONSE;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.RETURN_204;
import static com.inmobi.supply.sun.constant.PerfConstant.SUN_TERM_REASON;
import static com.inmobi.supply.sun.constant.ProtocolConstant.PROTO_BUF;
import static com.inmobi.supply.sun.utils.RandomUtils.dropByPercent;

/**
 * write info to serverHttpResponse
 *
 * @author remi.li
 */
@Slf4j
@Service
public class ResponseWriterFactory2 extends Worker2<BaseResponse, AsyncServletResponse> {
    @Autowired
    private SendKafkaUtils sendKafkaUtils;

    @Autowired
    private PrepareBidRequest prepareBidRequest;

    @Autowired
    private Map<String, IosModel> iosModelMap;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(BaseResponse baseResponse) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "ResponseWriterFactory");
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            LogUtils.logInfo(runTimeContext.getRequestId(),"enter ResponseWriterFactory2");
            if (runTimeContext.isError()) {
                return processIfError(runTimeContext, runTimeContext.getThrowable());
            }
            return doProcess(runTimeContext);
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new AsyncServletResponse());
        }));
    }

    public Mono<AsyncServletResponse> doProcess(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"ResponseWriterFactory2 doProcess");
        PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "ResponseWriterFactory");
        try {
            //1. build AsyncServletResponse
            AsyncServletResponse asyncServletResponse = runTimeContext.getAsyncServletResponse();
            BaseResponse baseResponse = runTimeContext.getBaseResponse();
            Charset outputCharset = baseResponse.getCharset();
            byte[] outputResponseBytes = baseResponse.getOutputBytes();
            String outputString = baseResponse.getOutputString();
            LogUtils.logInfo(runTimeContext.getRequestId(),"outputString={}, placementId={} sourceType={}",
                    outputString,
                    runTimeContext.getPlacementModel().getId(),
                    runTimeContext.getWinBidResponse().getSourceType());
            LogUtils.logInfo(runTimeContext.getRequestId(),"apiOutputResponseBytes {}",outputResponseBytes);
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
            if (runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                asyncServletResponse.setBaidu(true);
            }
            asyncServletResponse.setChannel(Optional.ofNullable(runTimeContext.getChannel()).orElse(""));
            asyncServletResponse.setRequestId(runTimeContext.getRequestId());
            SunRR.Builder sunRRBuilder = SunRR.getDefaultInstance().toBuilder();
            //2.build RR data
            buildSunRR(sunRRBuilder, runTimeContext, null);
            //3.send RR
            PerfUtils.perf("sendRR", "status", "valid");
            sendKafkaUtils.sendRR2(sunRRBuilder.build());
            //sendKafkaUtils.sendRR(sunRRBuilder.build());
            // 单独为有填充的请求发送RR
            if (sunRRBuilder.hasWinBidResponse()) {
                sendKafkaUtils.sendServedRR(sunRRBuilder.build());
            }
            List<WinNotice> winNotices = runTimeContext.getWinNoticeList();
            if (CollectionUtils.isNotEmpty(runTimeContext.getLostNoticeList().entrySet())) {
                return Flux.fromIterable(runTimeContext.getLostNoticeList().entrySet())
                        .flatMap(entry -> {
                            List<WinNotice> lostNotices = entry.getValue();
                            return Flux.fromIterable(winNotices)
                                    .defaultIfEmpty(WinNotice.getDefaultInstance())
                                    .mergeWith(Flux.fromIterable(lostNotices))
                                    .flatMap(url -> {
                                        if (!url.equals(WinNotice.getDefaultInstance()) && StringUtils.isNotBlank(url.getUrl())) {
                                            //4.send winNotice & lostNotice
                                            PerfUtils.perf("sendWinNotice", "dsp", runTimeContext.getWinBidResponse().getSaturnDsp());
                                            sendKafkaUtils.sendWinNotice2(url);
                                        }
                                        return Mono.just(asyncServletResponse);
                                    }).last();
                        }).last();
            } else {
                return Flux.fromIterable(winNotices)
                        .defaultIfEmpty(WinNotice.getDefaultInstance())
                        .flatMap(url -> {
                            if (!url.equals(WinNotice.getDefaultInstance()) && StringUtils.isNotBlank(url.getUrl()) ) {
                                //4.send winNotice & lostNotice

                                PerfUtils.perf("sendWinNotice", "dsp", runTimeContext.getWinBidResponse().getSaturnDsp());
                                sendKafkaUtils.sendWinNotice2(url);
                            }
                            return Mono.just(asyncServletResponse);
                        }).last();
            }
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void buildSunRR(SunRR.Builder sunRRBuilder, RunTimeContext runTimeContext, TermCode termCode) {
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
            LogUtils.logInfo(runTimeContext.getRequestId(),"unKnow.osType.occur, bidRequest={}", runTimeContext.getBidRequest());
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
            //runTimeContext.getPlacementModel().getFixRevShareList().forEach(revShare -> cosmosRevShare.put(revShare.getRevShareType(), revShare.getRevShare().doubleValue()));
            auctionBuilder.putAllCosmosRevShare(cosmosRevShare);
            LogUtils.logInfo(runTimeContext.getRequestId(),"defaultPe {}, realPe {}",
                    Optional.ofNullable(runTimeContext.getPlacementModel().getPubExpectation()).orElse(0L), runTimeContext.getRealRe());
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
        /*if (runTimeContext.getRequestType() == RequestType.SDK) {
            sunRRBuilder.setSunRequest(SunRequest.newBuilder().putAllSdkRequest(runTimeContext.getSdkRequest()).build());
        } else {
            sunRRBuilder.setSunRequest(SunRequest.newBuilder().setApiV3Request(Optional.ofNullable(runTimeContext.getApiv3Request()).orElse(ApiV3Request.getDefaultInstance())).build());
        }*/

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
        OriginalRequest originalRequest = buildOriginalRequest(runTimeContext, termCode);
        sunRRBuilder.setOriginalRequest(originalRequest);

        try {
            //add adx info
            sunRRBuilder.setAccountType(runTimeContext.getAccountType());
            RequestAdapterResponse requestAdapterResponse = runTimeContext.getRequestAdapterResponse();
            if (null != requestAdapterResponse) {
                JsonObject publisherSpecificFields = requestAdapterResponse.getPublisherSpecificFields();
                if (!Objects.isNull(publisherSpecificFields)) {
                    String adxAppId = Objects.isNull(publisherSpecificFields.get("adxAppId")) ? "" : publisherSpecificFields.get("adxAppId").getAsString();
                    String adxSlotId = Objects.isNull(publisherSpecificFields.get("adxSlotId")) ? "" : publisherSpecificFields.get("adxSlotId").getAsString();
                    sunRRBuilder.setAdxAppid(adxAppId);
                    sunRRBuilder.setAdxSlotid(adxSlotId.replaceAll(" ", ""));
                }
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"SunRR add adx info error {}", e);
        }
        // 添加反作弊response
        try {
            if (runTimeContext.isExecuteAntiFraud()) {
                sunRRBuilder.setExecuteAntiFraud(true);
                if (runTimeContext.getEarthResponse() != null) {
                    sunRRBuilder.setAfResponse(AfResponse.newBuilder()
                            .setScore(runTimeContext.getEarthResponse().getScore())
                            .addAllDropReasons(runTimeContext.getEarthResponse().getDropReasonsList())
                            .build());
                } else {
                    LogUtils.logInfo(runTimeContext.getRequestId(), "SunRR build found earthResponse is empty!");
                }
            }
            //添加日志信息
            if (null != termCode) {
                sunRRBuilder.setNewTermCode(
                        cn.inmobi.pb.sun.rr.TermCode.newBuilder()
                                .setCode(termCode.getCode())
                                .setReason(Optional.ofNullable(termCode.getDescription()).orElse(""))
                                .setCutField(false)
                );
            }
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "addEarthError {}", e);
        }
        Map<String, String> union = new HashMap<>();
        if(MapUtils.isNotEmpty(runTimeContext.getAbTestTags())){
            union.putAll(runTimeContext.getAbTestTags());
        }
        runTimeContext.getOriginBidResponseList().forEach(bidResponse -> {
            if (!Objects.isNull(bidResponse) && CollectionUtils.isNotEmpty(bidResponse.getAbTagsList())) {
                bidResponse.getAbTagsList().forEach(tag->union.put(tag, "true"));
            }
        });
        if(MapUtils.isNotEmpty(union)) {
            sunRRBuilder.putAllAbTestTags(union);
        }
        //TODO
        long nowLong = System.currentTimeMillis();
        LogUtils.logInfo(runTimeContext.getRequestId(),"sunRRProcessTime:{} ms",(nowLong - runTimeContext.getStartTime()));
        sunRRBuilder.setEndProcessTime((int) (nowLong - runTimeContext.getStartTime()));
        sunRRBuilder.setSaturnProcessTime(runTimeContext.getSaturnProcessTime());
        LogUtils.logInfo(runTimeContext.getRequestId()," sunRR  {} termCode {}", runTimeContext.getRequestId(), sunRRBuilder, termCode);
    }

    public Mono<AsyncServletResponse> processIfError(RunTimeContext runTimeContext, Throwable throwable) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"ResponseWriterFactory2 processIfError");
        TermCode termCode;
        try {
            if (throwable instanceof SunException) {
                SunException exception = (SunException) throwable;
                termCode = exception.getTermCode();
                runTimeContext.getTermCodes().add(exception.getTermCode());
                if (termCode.equals(TermCode.SERVER_ERROR)) {
                    LogUtils.logError(runTimeContext.getRequestId(),"sunException error {}", exception);
                    return Mono.just(new AsyncServletResponse());
                }
            } else {
                LogUtils.logError(runTimeContext.getRequestId(),"otherException error {}", throwable);
                return Mono.just(new AsyncServletResponse());
            }
            PerfUtils.perf(SUN_TERM_REASON, Tag.of("term", termCode.getDescription()));
            if (runTimeContext.getPlacementModel() != null && runTimeContext.getPlacementModel().getAccountId() != null && DynamicListConfig.ADAPTER_BAIDU_LIST.get().contains(runTimeContext.getPlacementModel().getAccountId())) {
                PerfUtils.perf("debugBaidu", Tag.of("term", termCode.getDescription()));
            }
            //1. 不管流程是否异常，都要发RR日志
            SunRR.Builder sunRRBuilder = SunRR.getDefaultInstance().toBuilder();
            try {
                buildSunRR(sunRRBuilder, runTimeContext, termCode);
            } catch (Exception e) {
                LogUtils.logError(runTimeContext.getRequestId(),"buildSunRR error {}", e);
            }
            if (termCode.isNeedSendRR()) {
                PerfUtils.perf("sendRR", "status", "invalid");

                // 暂时去掉 sun request和sun response  减小 sunrr包体
                sunRRBuilder.setSunRequest(SunRequest.getDefaultInstance());
                sunRRBuilder.setSunResponse(SunResponse.getDefaultInstance());
                //log.info("sunRRSizeErrorBeforeLength:{},sunRRSizeErrorAfterLength:{},requestId:{}",length, sunRRBuilder.build().toByteArray().length,runTimeContext.getRequestId());

                //日志精简
                simpleLog(sunRRBuilder,runTimeContext);
                //sendKafkaUtils.sendRR2(sunRRBuilder.build());
            }
            //2. 不管流程是否异常，都要构造最终的serverHttpResponse
            AsyncServletResponse asyncServletResponse = Optional.ofNullable(runTimeContext.getAsyncServletResponse()).orElse(new AsyncServletResponse());
            if (runTimeContext.getPlacementId() != null) {
                asyncServletResponse.setPlacementId(runTimeContext.getPlacementId());
            }
            asyncServletResponse.setTermCode(termCode.getCode());
            AdType adType = runTimeContext.getAdType();
            if (!Objects.isNull(adType)) {
                asyncServletResponse.setAdName(String.valueOf(adType.getNumber()));
            }
            if (!Objects.isNull(runTimeContext.getPlacementModel()) && runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                asyncServletResponse.setBaidu(true);
            }
            MediaType mediaType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.APPLICATION_JSON);
            String pubAccountId = (runTimeContext.getPlacementModel() != null) ? Optional.ofNullable(runTimeContext.getPlacementModel().getAccountId()).orElse("") : "";
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
                if (runTimeContext.getRequestType() == RequestType.API_4_0) {
                    asyncServletResponse.setStatus(HttpStatus.OK);
                    com.stars.bid.pb.BidResponse.Builder requestV4Builder = com.stars.bid.pb.BidResponse.newBuilder();
                    requestV4Builder.setProcessTimeMs((int) (System.currentTimeMillis()-runTimeContext.getStartTime()));
                    if (StringUtils.isNotEmpty(runTimeContext.getRequestId())) {
                        requestV4Builder.setRequestId(runTimeContext.getRequestId());
                    }
                    boolean isV4TraceEnable = SymbolString.TRUE.equalsIgnoreCase(runTimeContext.getHeaders().get(StringUtils.lowerCase(RequestHeader.V4_TRACE_ENABLED_HEADER)));
                    // trace模式
                    if (isV4TraceEnable) {
                        requestV4Builder.setErrorMessage(termCode.getDescription());
                        asyncServletResponse.setResponseBody(requestV4Builder.build().toByteArray());
                    } else if (termCode.isErrorDisplay()) {
                        requestV4Builder.setErrorMessage(termCode.getDescription());
                        asyncServletResponse.setResponseBody(requestV4Builder.build().toByteArray());
                    } else {
                        asyncServletResponse.setResponseBody(requestV4Builder.build().toByteArray());
                    }
                    LogUtils.logInfo(runTimeContext.getRequestId(),"apiTestResponse info {},requestV4BuilderByte {}",requestV4Builder.build(),requestV4Builder.build().toByteArray());
                }
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
                LogUtils.logInfo(runTimeContext.getRequestId(),"apiTestTermCode info {}",asyncServletResponse.getResponseBody());
                return Mono.just(asyncServletResponse);
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
                        asyncServletResponse.setResponseBody(noFillResponse);
                    }
                } else {
                    asyncServletResponse.setCharacterEncoding(Charsets.UTF_8);
                    asyncServletResponse.setContentType(runTimeContext.getServerRequest().headers().contentType().orElse(RuntimeConstant.JSON_CONTENT_UTF8_TYPE));
                    asyncServletResponse.setResponseBody(noFillResponse);
                }
            }
            return Mono.just(asyncServletResponse);
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "send error sunRR error {}", e);
        }
        return Mono.just(new AsyncServletResponse());
    }


    /**
     * 构建原始请求参数
     *
     * @param runTimeContext
     */
    private OriginalRequest buildOriginalRequest(RunTimeContext runTimeContext, TermCode termCode) {
        try {
            OriginalRequest.Builder originalRequestBuilder = runTimeContext.getOriginalRequestBuilder();
            originalRequestBuilder.setRequestId(Optional.ofNullable(runTimeContext.getRequestId()).orElse(""));
            originalRequestBuilder.setPubAccountId((runTimeContext.getPlacementModel() != null) ? Optional.ofNullable(runTimeContext.getPlacementModel().getAccountId()).orElse("") : "");
            RequestType requestType = runTimeContext.getRequestType();
            if (requestType.isSdk()) {
                originalRequestBuilder.setIntegrationType(IntegrationType.SDK_VALUE);
            } else {
                originalRequestBuilder.setIntegrationType(IntegrationType.API_VALUE);
            }

            if (!runTimeContext.getRequestType().isSdk()) {
                if(null != runTimeContext.getRequestType() && null != runTimeContext.getRequestType().getVersion()){
                    int major = runTimeContext.getRequestType().getVersion().getMajor();
                    int minor = runTimeContext.getRequestType().getVersion().getMinor();
                    int micro = runTimeContext.getRequestType().getVersion().getMicro();
                    originalRequestBuilder.setApiVersion(String.valueOf(major).concat(".").concat(String.valueOf(minor)).concat(".").concat(String.valueOf(micro)));
                }
            } else {
                originalRequestBuilder.setSdkVersion(StringUtils.isNotBlank(runTimeContext.getSdkVersion()) ? runTimeContext.getSdkVersion() : "");
                originalRequestBuilder.setHmsCoreVersion(Optional.ofNullable(runTimeContext.getSdkRequest().get(SdkParam.HMS_CORE_VERSION.of())).orElse(""));
                originalRequestBuilder.setAgVersion(Optional.ofNullable(runTimeContext.getSdkRequest().get(SdkParam.AG_VERSION.of())).orElse(""));
            }

            PlacementModel placementModel = runTimeContext.getPlacementModel();
            if (!Objects.isNull(placementModel)) {
                originalRequestBuilder.setAppId(Optional.ofNullable(placementModel.getAppId()).orElse(""));
            }
            BaseRequest baseRequest = runTimeContext.getBaseRequest();

            OriginalDevice.Builder builder = OriginalDevice.newBuilder();
            if(null != runTimeContext.getOriginalWidth()){
                builder.setOriginalWidth(runTimeContext.getOriginalWidth());
            }
            if(null != runTimeContext.getOriginalRealHeight()){
                builder.setOriginalHeight(runTimeContext.getOriginalRealHeight());
            }
            OriginalGeo.Builder originalGeoBuilder = OriginalGeo.newBuilder();
            originalRequestBuilder.setUa(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""));
            originalRequestBuilder.setIp(Optional.ofNullable(runTimeContext.getUserIpV4()).orElse(""));
            originalRequestBuilder.setIpv6(Optional.ofNullable(runTimeContext.getUserIpV6()).orElse(""));
            Device device = runTimeContext.getDevice();
            if (null != device && null != device.getId()) {
                Device.IdMapping id = device.getId();
                if (OsType.ANDROID.equals(device.getOsType())) {
                    builder.setOaid(Optional.ofNullable(id.getOaid()).orElse(""))
                            .setAndroidIdSha1(Optional.ofNullable(id.getO1()).orElse(""))
                            .setImeiMd5(Optional.ofNullable(id.getImeiMd5()).orElse(""))
                            .setAndroidIdMd5(Optional.ofNullable(id.getUm5()).orElse(""))
                            .setOaidMd5(Optional.ofNullable(id.getOaidMd5()).orElse(""))
                            .setOsType(OsType.ANDROID.getNumber());
                } else if (OsType.IOS.equals(device.getOsType())) {
                    builder.setIdfa(Optional.ofNullable(id.getIdfa()).orElse(""))
                            .setIdfaMd5(Optional.ofNullable(id.getIdfaMd5()).orElse(""))
                            .setIdfv(Optional.ofNullable(id.getIdfv()).orElse(""))
                            .setOsType(OsType.IOS.getNumber());
                }
            }


            if (null != device) {
                builder.setBootMark(Optional.ofNullable(device.getBootMark()).orElse(""));
                builder.setUpdateMark(Optional.ofNullable(device.getUpdateMark()).orElse(""));

                ConnectionType connectType = runTimeContext.getDevice().getConnectType();
                if (!Objects.isNull(connectType)) {
                    builder.setInmobiConnType(runTimeContext.getDevice().getConnectType().getNumber());
                }
                CaidModel caidModel = device.getCaidModel();
                if (!Objects.isNull(caidModel)) {
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

                builder.setOrientation(runTimeContext.getDevice().getDeviceOrientation());
                builder.setConnTypeSource(runTimeContext.getDevice().getConnTypeSource());
                builder.setCarrierSource(Optional.of(runTimeContext.getDevice().getCarrierSource()).orElse(0));
                builder.setDpi(Optional.ofNullable(runTimeContext.getDevice().getDeviceDensity()).orElse(0F));


                Carrier carrier = runTimeContext.getDevice().getCarrier();
                if (!Objects.isNull(carrier)) {
                    builder.setCarrier(Optional.of(carrier.getNumber()).orElse(0));
                }
            }

            if (StringUtils.isNotBlank(runTimeContext.getOriginalConnType())) {
                builder.setOriginalConnType(Integer.parseInt(runTimeContext.getOriginalConnType()));
            } else {
                builder.setOriginalConnType(0);
            }

            if (null != runTimeContext.getBaseRequest() && null != runTimeContext.getBaseRequest().getDevice()) {
                cn.inmobi.pb.sun.rr.base.Device.Geo geoPb = runTimeContext.getBaseRequest().getDevice().getGeo();
                if (geoPb != null) {
                    //SDK 暂时未取值
                    originalGeoBuilder.setOriginalLatitude(geoPb.getLat());
                    originalGeoBuilder.setOriginalLongitude(geoPb.getLon());
                }
            }

            if (null != runTimeContext.getBidRequest() && null != runTimeContext.getBidRequest().getDevice()) {
                //todo bidrequest (inmobi\original)
                int deviceType = runTimeContext.getBidRequest().getDevice().getDeviceType();
                builder.setDeviceType(deviceType);
            }

            builder.setOriginalModel(Optional.ofNullable(runTimeContext.getOriginalModel()).orElse(""));
            builder.setOriginalVendor(Optional.ofNullable(runTimeContext.getOriginalVendor()).orElse(""));

            builder.setInmobiVendor(Optional.ofNullable(runTimeContext.getInmobiVendor()).orElse(""));
            builder.setInmobiModel(Optional.ofNullable(runTimeContext.getInmobiModel()).orElse(""));
            builder.setInmobiWidth(Optional.ofNullable(runTimeContext.getInmobiWidth()).orElse(0));
            builder.setInmobiRealHeight(Optional.ofNullable(runTimeContext.getInmobiRealHeight()).orElse(0));
            //builder.setRomVersion(Optional.of(runTimeContext.getBidRequest().getDevice().getRomVersion()).orElse(""));
            builder.setLmt(runTimeContext.getLat());
            originalRequestBuilder.setDevice(builder.build());

            originalGeoBuilder.setInmobiLatitude(runTimeContext.getInmobiLatitude());
            originalGeoBuilder.setInmobiLongitude(runTimeContext.getInmobiLongitude());
            if (!Objects.isNull(baseRequest)) {
                originalGeoBuilder.setOriginalCityCode(Optional.of(baseRequest.getDevice().getGeo().getCity()).orElse(""));
                originalGeoBuilder.setOriginalProvinceCode(Optional.of(baseRequest.getDevice().getGeo().getProvince()).orElse(""));
                originalGeoBuilder.setOriginalDistrictCode(Optional.of(baseRequest.getDevice().getGeo().getDistrict()).orElse(""));
            }

            Geo geo = runTimeContext.getGeo();
            if (!Objects.isNull(geo)) {
                originalGeoBuilder.setInmobiCityCode(Optional.of(geo.getCityCode()).orElse(""));
                originalGeoBuilder.setInmobiProvinceCode(Optional.of(geo.getProvinceCode()).orElse(""));
                originalGeoBuilder.setInmobiDistrictCode(Optional.of(geo.getDistrictCode()).orElse(""));
                originalGeoBuilder.setInmobiCaaCityCode(Optional.of(geo.getCaaCityCode()).orElse(""));
                originalGeoBuilder.setInmobiCaaProvinceCode(Optional.of(geo.getCaaProvinceCode()).orElse(""));
            }
            originalRequestBuilder.setGeo(originalGeoBuilder.build());

            OriginalUser.Builder originalUserBuilder = OriginalUser.newBuilder();
            if (!Objects.isNull(baseRequest)) {
                cn.inmobi.pb.sun.rr.base.User user = baseRequest.getUser();
                String gender = user.getGender();
                if (StringUtils.isNotBlank(gender)) {
                    originalUserBuilder.setGender("m".equals(gender) ? 1 : 2);
                } else {
                    originalUserBuilder.setGender(0);
                }
                originalUserBuilder.setAge(Optional.of(user.getExt().getAge()).orElse(0));
            }
            originalUserBuilder.setCsid(Optional.ofNullable(runTimeContext.getAllCsids()).orElse(""));
            originalRequestBuilder.setOriginalUser(originalUserBuilder.build());
            if (null != termCode) {
                originalRequestBuilder.setTermCode(termCode.getCode());
            } else {
                originalRequestBuilder.setTermCode(0);
            }
            return originalRequestBuilder.build();
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"SunRR build originRequest error {}", e);
        }
        return OriginalRequest.getDefaultInstance();
    }

    //日志精简
    private void simpleLog(SunRR.Builder sunRRBuilder, RunTimeContext runTimeContext) {
        SunRR sunRR = sunRRBuilder.build();
        boolean executeAntiFraud = sunRR.getExecuteAntiFraud();
        if (executeAntiFraud) {
            PerfUtils.perf("sun.simpleLog", "simpleLogCount", "allEarth");
            sendKafkaUtils.sendRR2(sunRR);
        } else {
            try {
                if (dropByPercent(DynamicIntegerConfig.LOG_SIMPLE_RATE.get())) {
                    PerfUtils.perf("sun.simpleLog", "simpleLogCount", "noSimple");
                    sendKafkaUtils.sendRR2(sunRR);
                } else {
                    PerfUtils.perf("sun.simpleLog", "simpleLogCount", "simple");
                    //对sunRR 日志进行精简
                    SunRR sunRRSimple = logSimple(sunRR);
                    sendKafkaUtils.sendRR2(sunRRSimple);
                }
            } catch (Exception e) {
                LogUtils.logError(runTimeContext.getRequestId(),"simpleLog error {}",e);
            }
        }
    }

    //对sunRR日志进行精简
    private SunRR logSimple(SunRR build) {
        SunRR.Builder sunRRBuilder = SunRR.getDefaultInstance().toBuilder();
        sunRRBuilder.setRequestId(Optional.ofNullable(build.getRequestId()).orElse(""));
        sunRRBuilder.setAdxSlotid(Optional.ofNullable(build.getAdxSlotid()).orElse(""));
        sunRRBuilder.setAdxAppid(Optional.ofNullable(build.getAdxAppid()).orElse(""));
        sunRRBuilder.setAccountType(build.getAccountType());
        sunRRBuilder.setTimestamp(build.getTimestamp());

        BidRequest bidRequest = build.getBidRequest();
        if(null != bidRequest){
            BidRequest.Builder bidRequestBuilder = BidRequest.getDefaultInstance().toBuilder();
            List<AdSlot> adslotsList = bidRequest.getAdslotsList();
            List<AdSlot> newAdSlot = adslotsList.stream().map(adSlot -> AdSlot.newBuilder().setId(adSlot.getId()).build()).collect(Collectors.toList());
            bidRequestBuilder.addAllAdslots(newAdSlot);

            App app = bidRequest.getApp();
            if(null != app){
                App appBuild = App.newBuilder().setPackageName(Optional.ofNullable(app.getPackageName()).orElse(""))
                        .setBundleId(Optional.ofNullable(app.getBundleId()).orElse(""))
                        .setName(Optional.ofNullable(app.getName()).orElse("")).build();
                bidRequestBuilder.setApp(appBuild);
            }

            bidRequestBuilder.setSdkVersion(Optional.ofNullable(bidRequest.getSdkVersion()).orElse(""));

            cn.inmobi.pb.api.Device device = bidRequest.getDevice();
            if(null != device){
                int deviceType = device.getDeviceType();
                cn.inmobi.pb.api.Device deviceBuild = cn.inmobi.pb.api.Device.newBuilder().setDeviceType(deviceType)
                        .setVendor(Optional.ofNullable(device.getVendor()).orElse(""))
                        .setModel(Optional.ofNullable(device.getModel()).orElse("")).build();
                bidRequestBuilder.setDevice(deviceBuild);
            }

            Version apiVersion = bidRequest.getApiVersion();
            if(null != apiVersion){
                bidRequestBuilder.setApiVersion(apiVersion);
            }
            sunRRBuilder.setBidRequest(bidRequestBuilder);
        }

        OriginalRequest originalRequest = build.getOriginalRequest();
        if(null != originalRequest){
            OriginalRequest.Builder builder = OriginalRequest.getDefaultInstance().toBuilder();
            OriginalDevice device = originalRequest.getDevice();
            if(null != device){
                OriginalDevice originalDeviceBuild = OriginalDevice.newBuilder().setOsType(device.getOsType()).build();
                builder.setDevice(originalDeviceBuild);
            }
            OriginalRequest newOriginalRequest = builder.setPubAccountId(Optional.ofNullable(originalRequest.getPubAccountId()).orElse(""))
                    .setIntegrationType(originalRequest.getIntegrationType())
                    .setTermCode(originalRequest.getTermCode()).build();
            sunRRBuilder.setOriginalRequest(newOriginalRequest);
        }

        cn.inmobi.pb.sun.rr.TermCode newTermCode = build.getNewTermCode();
        if(null != newTermCode){
            cn.inmobi.pb.sun.rr.TermCode newTermCodeBuild = newTermCode.toBuilder().setCutField(true).build();
            sunRRBuilder.setNewTermCode(newTermCodeBuild);
        }
        return sunRRBuilder.build();
    }

}