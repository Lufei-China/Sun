package com.inmobi.supply.sun.service.postProc.format.module;

import cn.inmobi.pb.AdInfo;
import cn.inmobi.pb.EventRR;
import cn.inmobi.pb.api.*;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.protobuf.ProtocolStringList;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.SaturnDsp;
import com.inmobi.supply.sun.constant.TrackerEventInfo;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.AdRunTimeInfo;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.stars.bid.pb.Tracker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

import static com.inmobi.supply.sun.constant.RuntimeConstant.FEN_TO_YUAN;
import static com.inmobi.supply.sun.utils.PriceEncryptUtils.needEncryptDsp;
import static com.inmobi.supply.sun.utils.PriceEncryptUtils.priceEncrypt;
import static com.inmobi.supply.sun.utils.PriceEncryptUtils.replaceMacroPrice;
import static com.inmobi.supply.sun.utils.WorkerUtils.httpsTrans;

/**
 * generate ad tracker event & urls
 *
 * @author remi.li
 */
@Service
@Slf4j
public class TrackingUrlGen implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "TrackingUrlGen");
        Ad winAd = runTimeContext.getWinAd();
        BidResponse winBid = runTimeContext.getWinBidResponse();
        AdRunTimeInfo adRunTimeInfo = new AdRunTimeInfo();
        EventRR eventRR = buildEventRR(runTimeContext, winBid);
        List<Tracking> trackingList = winAd.getCreative().getTrackingEventList();

        //Map<Integer, List<String>> trackerUrlMap = trackingList.stream().collect(Collectors.toMap(Tracking::getEvent, tracking -> replaceMacro(tracking.getUrlsList(), winBid, runTimeContext)));
        Map<Integer, List<String>> trackerUrlMap = new HashMap<>();
        List<Integer> collect = trackingList.stream().map(Tracking::getEvent).distinct().collect(Collectors.toList());
        for (Integer eventType : collect) {
            List<String> strings = new ArrayList<>();
            List<Tracking> collect2 = trackingList.stream().filter(tracking -> tracking.getEvent() == eventType).collect(Collectors.toList());
            for (Tracking tracking : collect2) {
                List<String> list = replaceMacro(tracking.getUrlsList(), winBid, runTimeContext);
                strings.addAll(list);
            }
            trackerUrlMap.put(eventType,strings);
        }


        List<String> winNoticeUrlList = replaceMacro(winAd.getCreative().getWinNoticeUrlList(), winBid, runTimeContext);
        buildWinNotice(runTimeContext, winBid, winNoticeUrlList);
        List<String> clickTrackers = replaceMacro(winAd.getCreative().getClickUrlList(), winBid, runTimeContext);
        List<String> viewTrackers = replaceMacro(winAd.getCreative().getImpUrlList(), winBid, runTimeContext);
        LogUtils.logInfo(runTimeContext.getRequestId(), "enter Envelope2Service TrackingUrlGen");
        return TrackerEventInfo.addBeaconUrl(eventRR, runTimeContext)
                .flatMap(trackingEventListTuple2 -> {
                    TrackingEvent trackingEvent = trackingEventListTuple2.getT1();
                    List<String> inmobiTrackingList = trackingEventListTuple2.getT2();
                    List<String> advTrackers = trackerUrlMap.getOrDefault(trackingEvent.getNumber(), new ArrayList<>());
                    if (CollectionUtils.isNotEmpty(advTrackers)) {
                        inmobiTrackingList.addAll(advTrackers);
                        if (winAd.getSaturnChannelId().equals(SaturnDsp.GDT_CUSTOM_CHANNEL_ID)) {
                            adRunTimeInfo.getGdtEventTrackers().put(trackingEvent, advTrackers);
                        }
                    }
                    if (trackingEvent == TrackingEvent.CLICK) {
                        if (CollectionUtils.isNotEmpty(clickTrackers)) {
                            inmobiTrackingList.addAll(clickTrackers);
                        }
                    }
                    if (trackingEvent == TrackingEvent.VIEW) {
                        if (CollectionUtils.isNotEmpty(viewTrackers)) {
                            inmobiTrackingList.addAll(viewTrackers);
                        }
                    }
                    if (CollectionUtils.isNotEmpty(inmobiTrackingList)) {
                        adRunTimeInfo.getEventTrackers().put(trackingEvent, inmobiTrackingList);
                    }
                    return Mono.just(adRunTimeInfo);
                }).last().flatMap(runTimeInfo -> {
                    runTimeContext.setAdRunTimeInfo(runTimeInfo);
                    return Mono.just(0);
                }).onErrorResume(throwable -> {
                    log.error("TrackingUrlGen process error requestId {}", runTimeContext.getRequestId(), throwable);
                    return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, throwable));
                });
    }

    private void buildWinNotice(RunTimeContext runTimeContext, BidResponse winBid, List<String> winNoticeUrlList) {
        if (CollectionUtils.isNotEmpty(winNoticeUrlList)) {
            PerfUtils.perf("haveWinNotice", "dsp", winBid.getSaturnDsp());
        }
        winNoticeUrlList.forEach(url -> {
            WinNotice.Builder winNoticeBuilder = WinNotice.newBuilder();
            winNoticeBuilder.setMethod(WinNotice.Method.GET);
            winNoticeBuilder.setEvent(WinNotice.Event.WIN);
            winNoticeBuilder.setUrl(url);
            winNoticeBuilder.setSaturnDsp(Optional.ofNullable(winBid.getSaturnDsp()).orElse(""));
            winNoticeBuilder.setTimestamp(System.currentTimeMillis());
            winNoticeBuilder.setRequestId(runTimeContext.getRequestId());
            runTimeContext.getWinNoticeList().add(winNoticeBuilder.build());
        });
    }

    private EventRR buildEventRR(RunTimeContext runTimeContext, BidResponse winBid) {
        EventRR.Builder eventRRBuilder = EventRR.newBuilder();
        eventRRBuilder.setRequestId(runTimeContext.getRequestId());
        eventRRBuilder.setAdslotId(String.valueOf(runTimeContext.getPlacementId()));
        eventRRBuilder.setOsType(runTimeContext.getDevice().getOsType().getNumber());
        eventRRBuilder.setAdType(runTimeContext.getAdType().getNumber());
        eventRRBuilder.setAppId(Optional.ofNullable(runTimeContext.getPlacementModel().getAppId()).orElse(""));
        eventRRBuilder.setUserIp(StringUtils.isNotBlank(runTimeContext.getUserIpV4()) ? runTimeContext.getUserIpV4() : "");
        eventRRBuilder.setUseragent(Optional.ofNullable(runTimeContext.getDevice().getUserAgent()).orElse(""));
        eventRRBuilder.setServerIp(Optional.ofNullable(runTimeContext.getServerIp()).orElse(""));
        eventRRBuilder.setResponseTimestamp(String.valueOf(System.currentTimeMillis()));
        eventRRBuilder.setUserIpv6(StringUtils.isNotBlank(runTimeContext.getUserIpV6()) ? runTimeContext.getUserIpV6() : "");
        eventRRBuilder.setSourceType(winBid.getSourceType());
        eventRRBuilder.setPmp(runTimeContext.getWinAd().getPmp());
        if (runTimeContext.getRequestType().isSdk()) {
            eventRRBuilder.setIntegrationType(IntegrationType.SDK_VALUE);
            eventRRBuilder.setIntegrationVersion(runTimeContext.getBidRequest().getSdkVersion());
        } else {
            eventRRBuilder.setIntegrationType(IntegrationType.API_VALUE);
            Version version = runTimeContext.getRequestType().getVersion();
            eventRRBuilder.setIntegrationVersion(StringUtils.join(Lists.newArrayList(version.getMajor(), version.getMinor(), version.getMicro()), "."));
        }
        Device device = runTimeContext.getDevice();
        eventRRBuilder.setDeviceId(DeviceId.newBuilder()
                .setImbId(Optional.ofNullable(winBid.getImbId()).orElse(""))
                .setIdfa(Optional.ofNullable(device.getId().getIdfa()).orElse(""))
                .setIdfv(Optional.ofNullable(device.getId().getIdfv()).orElse(""))
                .setImei(Optional.ofNullable(device.getId().getImei()).orElse(""))
                .setSsid(Optional.ofNullable(device.getId().getSsid()).orElse(""))
                .setWifiMac(Optional.ofNullable(device.getId().getSsid()).orElse(""))
                .setDsid(Optional.ofNullable(device.getId().getDsid()).orElse(""))
                .setOpenUdid(Optional.ofNullable(device.getId().getOpenUdid()).orElse(""))
                .setImeiMd5(Optional.ofNullable(device.getId().getImeiMd5()).orElse(""))
                .setAndroidId(Optional.ofNullable(device.getId().getAndroidId()).orElse(""))
                .setUuid(Optional.ofNullable(device.getId().getUuid()).orElse(""))
                .setOaid(Optional.ofNullable(device.getId().getOaid()).orElse(""))
                .setAndroidIdMd5(Optional.ofNullable(device.getId().getUm5()).orElse(""))
                .setAndroidIdSha1(Optional.ofNullable(device.getId().getO1()).orElse(""))
                .setIdfaMd5(Optional.ofNullable(device.getId().getIdfaMd5()).orElse(""))
                .setOaidMd5(Optional.ofNullable(device.getId().getOaidMd5()).orElse("")));
        AdInfo.Builder adInfoBuilder = AdInfo.newBuilder();
        Ad winAd = runTimeContext.getWinAd();
        adInfoBuilder.setIsCachedAd(winBid.getIsCachedAd());
        adInfoBuilder.setAdvAccId(winAd.getAdvAccId());
        adInfoBuilder.setCampId(winAd.getCampId());
        adInfoBuilder.setGroupId(winAd.getGroupId());
        adInfoBuilder.setCreativeId(winAd.getAdId());
        adInfoBuilder.setAdvAppId(winAd.getCreative().getAdvAppId());
        adInfoBuilder.setEcpmPrice(winAd.getBidPrice());
        adInfoBuilder.setBidType(winAd.getBidType());
        adInfoBuilder.setNetworkMarkupRatio(winAd.getNetworkMarkupRatio());
        adInfoBuilder.setNetworkSettleType(winAd.getNetworkSettleType());
        if ((winBid.getSourceType() == SourceType.BRAND_VALUE
                || winBid.getSourceType() == SourceType.PERF_VALUE
                || winBid.getSourceType() == SourceType.REMARKETING_VALUE)
                && winAd.getSettleType() == SettleType.FIRST_VALUE) {
            adInfoBuilder.setWinPrice(Optional.ofNullable(runTimeContext.getFirstPrice()).orElse(0L));
        } else {
            adInfoBuilder.setWinPrice(Optional.ofNullable(runTimeContext.getSecondPrice()).orElse(0L));
        }
        if (runTimeContext.isMixAd()){
            adInfoBuilder.setAdvFreeSettlePrice((Optional.ofNullable(runTimeContext.getAdvSettlePrice()).orElse(0L)) * 100);
        } else {
            adInfoBuilder.setAdvSettlePrice((Optional.ofNullable(runTimeContext.getAdvSettlePrice()).orElse(0L)) * 100);
        }
        adInfoBuilder.setPubSettlePrice(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
        if (runTimeContext.getChargeAndPayMap().containsKey(ChargeAndPay.ORIGIN_PAY)) {
            adInfoBuilder.setOriginPubSettlePrice(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.ORIGIN_PAY));
        } else {
            //OriginPubSettlePrice字段不能为空
            adInfoBuilder.setOriginPubSettlePrice(runTimeContext.getChargeAndPayMap().get(ChargeAndPay.PAY));
        }
        adInfoBuilder.setAdvTotalDiscount(Optional.ofNullable(runTimeContext.getTotalDiscount()).orElse(0F));
        adInfoBuilder.setAdpoolDeduction(Optional.ofNullable(runTimeContext.getAdpoolDeduction()).orElse(0D));

        // marketPlace 逻辑处理
        if(runTimeContext.getSwitchFlag() == 1){
            PerfUtils.perf("sun.mkt.place.count", "status", "tracker");
            adInfoBuilder.setAdvBidPrice(runTimeContext.getAdvBidPrice());
            eventRRBuilder.setSwitchFlag(1);
        }
        adInfoBuilder.setPubAccountId(runTimeContext.getPlacementModel().getAccountId());
        if (winBid.getSourceType() == SourceType.SATURN_VALUE) {
            adInfoBuilder.setSaturnChannelId(Optional.ofNullable(winAd.getSaturnChannelId()).orElse(""));
            adInfoBuilder.setSaturnSegmentId(Optional.ofNullable(winAd.getSaturnSegmentId()).orElse(""));
        }
        adInfoBuilder.setLossP(winAd.getLossP());
        adInfoBuilder.setAdvSysAppId(winAd.getAdvSysAppId());
        //TODO BY REMI:NEED UPDATE
        adInfoBuilder.setTemplateId(0L);
        adInfoBuilder.setCvuFlag(winAd.getCreative().getCvuFlag());
        adInfoBuilder.setCreativeType(winAd.getCreative().getCreativeType());
        adInfoBuilder.setMaterialId(winAd.getCreative().getMaterialId());
        adInfoBuilder.setAttributionType(winAd.getAttributionType());
        //dpa 动态商品库
        adInfoBuilder.setSkuId(winAd.getSkuId());
        adInfoBuilder.setAdTemplateId(winAd.getAdTemplateId());

        adInfoBuilder.setDpaChannel(winAd.getDpaChannel());
        adInfoBuilder.setAppMixId(Optional.ofNullable(runTimeContext.getAppMixId()).orElse(""));
        adInfoBuilder.setAppMixName(Optional.ofNullable(runTimeContext.getAppMixName()).orElse(""));
        eventRRBuilder.setAdInfo(adInfoBuilder);

        if (MapUtils.isNotEmpty(runTimeContext.getExperimentFlagsMap())) {
            if (runTimeContext.getExperimentFlagsMap().containsKey("isDynamicTraffic")) {
                PerfUtils.perf("isDynamicFloor", "value", String.valueOf(runTimeContext.getExperimentFlagsMap().get("isDynamicTraffic")));
            }
            eventRRBuilder.addExperimentParam(ObjectMapperUtils.toJSON(runTimeContext.getExperimentFlagsMap()));
        }

        if (CollectionUtils.isNotEmpty(winAd.getResponseExperimentParamList())) {
            //TODO,这里不规范！需要矫正
            eventRRBuilder.addAllExperimentParam(winAd.getResponseExperimentParamList());
        }
        if (CollectionUtils.isNotEmpty(runTimeContext.getExperimentFlags())) {
            //TODO,这里不规范！需要矫正
            eventRRBuilder.addAllExperimentParam(runTimeContext.getExperimentFlags());
        }
        eventRRBuilder.setPredictBid(winAd.getCreative().getPredictBid());
        eventRRBuilder.setCityCode(Optional.ofNullable(runTimeContext.getGeo().getCityCode()).orElse(""));
        eventRRBuilder.setTest(runTimeContext.getBidRequest().getTest());
        eventRRBuilder.setRequestGeo(runTimeContext.getBidRequest().getGeo());
        eventRRBuilder.setRequestVendor(Optional.ofNullable(runTimeContext.getDevice().getVendor()).orElse(""));
        eventRRBuilder.setBundleId(Optional.ofNullable(runTimeContext.getBidRequest().getApp().getPackageName()).orElse(""));
        eventRRBuilder.setFloor(runTimeContext.getBaiduBrice());
        try{
            Map<String, String> abTestTags = runTimeContext.getAbTestTags();
            if (MapUtils.isNotEmpty(abTestTags)) {
                List<String> collect = new ArrayList<>(abTestTags.keySet());
                if (CollectionUtils.isNotEmpty(winBid.getAbTagsList())) {
                    winBid.getAbTagsList().addAll(collect);
                    eventRRBuilder.addAllAbTags(winBid.getAbTagsList());
                }else {
                    eventRRBuilder.addAllAbTags(collect);
                }
            }else {
                eventRRBuilder.addAllAbTags(winBid.getAbTagsList());
            }
            LogUtils.logInfo(runTimeContext.getRequestId(),"eventRR abTestTags {} tagList {}",abTestTags, eventRRBuilder.getAbTagsList());
        }catch (Exception e){
            LogUtils.logError(runTimeContext.getRequestId(),"eventRRTagListError",e);
        }
        eventRRBuilder.addAllAbBuckets(winBid.getAbBucketsList());

        if(StringUtils.isNotBlank(winBid.getSiteMappingSlotId())){
            eventRRBuilder.setSiteMappingSlotId(winBid.getSiteMappingSlotId());
        }

        LogUtils.logInfo(runTimeContext.getRequestId(),"eventRR {}",eventRRBuilder);
        return eventRRBuilder.build();
    }

    private List<String> replaceMacro(List<String> trackerList, BidResponse winBid, RunTimeContext runTimeContext) {
        return trackerList.stream()
                .map(trackerUrl -> {
                    trackerUrl = httpsTrans(runTimeContext, trackerUrl);
                    if (winBid.getSourceType() == SourceType.SATURN_VALUE) {
                        Ad winAd = winBid.getAds(0);
                        try {
                            BigDecimal originReplacePrice;
                            if (winAd.getSaturnChannelId().equals(SaturnDsp.PDD_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.JD_CUSTOM_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.MEITUAN_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.YIDIAN_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.DATA_ADS_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.ALI_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.KUAISHOU_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.MEITUAN_WAIMAI_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.GDT_CUSTOM_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.BXM_PG_RTB_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.DSP_360_CHANNEL_ID)
                                    || winAd.getSaturnChannelId().equals(SaturnDsp.HAI_LIANG_CHANNEL_ID)
                                    ) {
                                // price unit is fen
                                originReplacePrice = BigDecimal.valueOf(runTimeContext.getAdvSettlePrice());
                            } else {
                                originReplacePrice = BigDecimal.valueOf(runTimeContext.getAdvSettlePrice()).divide(FEN_TO_YUAN, 2, RoundingMode.HALF_UP);
                            }
                            String replacePrice;
                            if (needEncryptDsp(winAd.getSaturnChannelId())) {
                                //需要加密价格的dsp
                                try {
                                    replacePrice = priceEncrypt(winAd.getSaturnChannelId(), originReplacePrice.longValue());
                                } catch (Exception e) {
                                    log.error("priceEncrypt error", e);
                                    replacePrice = String.valueOf(originReplacePrice);
                                }
                            } else {
                                replacePrice = String.valueOf(originReplacePrice);
                            }
                            trackerUrl = replaceMacroPrice(trackerUrl, replacePrice, winAd.getSaturnChannelId(), winAd);
                            if (trackerUrl.contains("__OS__")) {
                                if (runTimeContext.getDevice()!=null) {
                                    if(runTimeContext.getDevice().getOsType()== OsType.ANDROID){
                                        trackerUrl = StringUtils.replace(trackerUrl, "__OS__", "0");
                                    }
                                    if(runTimeContext.getDevice().getOsType()== OsType.IOS){
                                        trackerUrl = StringUtils.replace(trackerUrl, "__OS__", "1");
                                    }
                                }
                            }
                            if (trackerUrl.contains("__IP__")) {
                                if (StringUtils.isNotBlank(StringUtils.isNotBlank(runTimeContext.getUserIpV4()) ? runTimeContext.getUserIpV4() : runTimeContext.getUserIpV6())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IP__", StringUtils.isNotBlank(runTimeContext.getUserIpV4()) ? runTimeContext.getUserIpV4() : runTimeContext.getUserIpV6());
                                }
                            }

                            if (trackerUrl.contains("__UA__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getUserAgent())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__UA__", URLEncoder.encode(runTimeContext.getDevice().getUserAgent(),"UTF-8"));
                                }
                            }

                            if (trackerUrl.contains("__IDFA__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getIdfa())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IDFA__", runTimeContext.getDevice().getId().getIdfa());
                                }
                            }
                            if (trackerUrl.contains("__IMEI__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getImeiMd5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IMEI__", runTimeContext.getDevice().getId().getImeiMd5());
                                }
                            }
                            if (trackerUrl.contains("__OAID__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getOaid())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__OAID__", runTimeContext.getDevice().getId().getOaid());
                                }
                            }
                            if (trackerUrl.contains("__OAID1__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getOaidMd5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__OAID1__", runTimeContext.getDevice().getId().getOaidMd5());
                                } else if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getOaid())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__OAID1__", DigestUtils.md5Hex(runTimeContext.getDevice().getId().getOaid()));
                                }
                            }
                            if (trackerUrl.contains("__ANDROIDID__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getUm5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__ANDROIDID__", runTimeContext.getDevice().getId().getUm5());
                                }
                            }
                            if (trackerUrl.contains("__ANDROID_ID_MD5__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getUm5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__ANDROID_ID_MD5__", runTimeContext.getDevice().getId().getUm5());
                                }
                            }
                            if (trackerUrl.contains("__IDFAMD5__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getIdfaMd5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IDFAMD5__", runTimeContext.getDevice().getId().getIdfaMd5());
                                } else if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getIdfa())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IDFAMD5__", DigestUtils.md5Hex(runTimeContext.getDevice().getId().getIdfa()));
                                }
                            }
                            if (trackerUrl.contains("__IDFA1__")) {
                                if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getIdfaMd5())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IDFA1__", runTimeContext.getDevice().getId().getIdfaMd5());
                                } else if (StringUtils.isNotBlank(runTimeContext.getDevice().getId().getIdfa())) {
                                    trackerUrl = StringUtils.replace(trackerUrl, "__IDFA1__", DigestUtils.md5Hex(runTimeContext.getDevice().getId().getIdfa()));
                                }
                            }

                            // caid 宏替换
                            if (trackerUrl.contains("__CAID__")) {
                                if (runTimeContext.getDevice().getOsType() == OsType.IOS && null != runTimeContext.getDevice() && null != runTimeContext.getDevice().getCaidModel()) {
                                    CaidModel caidModel = runTimeContext.getDevice().getCaidModel();
                                    String caid = caidModel.getCaid();
                                    if(StringUtils.isNotBlank(caid)){
                                        trackerUrl = StringUtils.replace(trackerUrl, "__CAID__", caid);
                                    }
                                }

                            }

                        } catch (Exception e) {
                            log.error("replaceMacro error", e);
                        }
                    }
                    return trackerUrl;
                }).collect(Collectors.toList());
    }
}