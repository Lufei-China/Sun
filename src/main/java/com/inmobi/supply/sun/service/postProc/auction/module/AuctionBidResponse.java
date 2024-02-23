package com.inmobi.supply.sun.service.postProc.auction.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import cn.inmobi.pb.api.*;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.google.api.client.util.Lists;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementRtbPe;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicMapConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.model.runtime.TouTiaoLostNotice;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.NO_VB_PROPORTION;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.TRUMP_DSP_CHANNEL_ID_LIST;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.WITHOUT_VB_CHANNEL_WHITE_LIST;
import static com.inmobi.supply.sun.constant.RuntimeConstant.ONE_POINT_ZERO_SIX;
import static com.inmobi.supply.sun.utils.UrlUtils.URLRequest;

/**
 * auction & charge and pay
 *
 * @author Leo.Liao
 */
@Service
@Slf4j
public class AuctionBidResponse implements ModuleWorker<AuctionDataType> {
    @Override
    public Mono<AuctionDataType> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"AuctionBidResponse");
            List<BidResponse> validBidResponseList = runTimeContext.getValidBidResponseList();
//            List<BidResponse> lowerThanFloorBidResponseList = runTimeContext.getLowerThanFloorBidResponseList();
            if (CollectionUtils.isEmpty(validBidResponseList)) {
                /*List<BidResponse> jdLowerThanFloorBidResponseList = lowerThanFloorBidResponseList.stream()
                        .filter(bidResponse -> bidResponse.getSourceType() == SourceType.SATURN_VALUE
                                && bidResponse.getAdsList().get(0).getSaturnChannelId().equals(SaturnDsp.JD_CHANNEL_ID))
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(jdLowerThanFloorBidResponseList)) {
                    //如果是京东低于底价的结果，参与动态底价逻辑，不走正常的auction逻辑
                    return Mono.just(0);
                } else {
                    throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                }*/
                throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
            }
            log.info("validBidResponseList={}, placementId={} pubAccountId={} adType={}",
                    validBidResponseList, runTimeContext.getPlacementId(),
                    runTimeContext.getPlacementModel().getAccountId(),
                    runTimeContext.getAdType().name());
            Map<FloorEntityType, BigDecimal> cosmosRevShare = runTimeContext.getCosRevShare();
            List<BidResponse> trumpBids = new ArrayList<>();
            // 啥用没有占用内存注释掉 ymc
            //List<BidResponse> brandTrumpBids = new ArrayList<>();
            //List<BidResponse> dspTrumpBids = new ArrayList<>();

            BidResponse winBidResponse = validBidResponseList.get(0);
            BidResponse secondBidResponse = null;
            BidResponse toutiaoBid = null;

            long firstPrice = 0;
            long secondPrice = 0;

            if (validBidResponseList.size() >= 2) {
                //multi response from different adPool
                //pick the best one
                //M0:compare price(should be improved)
                winBidResponse = null;
            }

            for (BidResponse bidResponse : validBidResponseList) {
                /**
                 * 各个渠道的填充监控
                 */
                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, SourceType.forNumber(bidResponse.getSourceType()).name()));
                /**
                 * 有response返回的分请求维度的监控
                 */
                PerfUtils.perf("ValidResponseRequestType", Tag.of("requestType", runTimeContext.getRequestType().name()),
                        Tag.of("adType", runTimeContext.getAdType().name()),
                        Tag.of("creativeType", CreativeType.forNumber(bidResponse.getAds(0).getCreative().getCreativeType()).name()),
                        Tag.of("os", runTimeContext.getDevice().getOsType().name()));
                // perf 监控
                if (bidResponse.getSourceType() == SourceType.PERF_VALUE) {
//                    PerfUtils.perf(PerfConstant.PERF_ID_RESPONSE,
//                            Tag.of("adId", bidResponse.getAds(0).getAdId()),
//                            Tag.of("advAccId", String.valueOf(bidResponse.getAds(0).getAdvAccId())),
//                            Tag.of("campId", String.valueOf(bidResponse.getAds(0).getCampId())),
//                            Tag.of("groupId", String.valueOf(bidResponse.getAds(0).getGroupId())));
                }
                if (bidResponse.getSourceType() == SourceType.SATURN_VALUE) {
                    PerfUtils.perf(PerfConstant.PERF_SATURN_RESPONSE,
                            Tag.of("dsp", bidResponse.getSaturnDsp()),
                            Tag.of("adType", runTimeContext.getAdType().name()),
                            Tag.of("creativeType", CreativeType.forNumber(bidResponse.getAds(0).getCreative().getCreativeType()).name()),
                            Tag.of("os", runTimeContext.getDevice().getOsType().name()));
                }
                // remarketing 的直接放入胜出bids
                if (FloorEntityType.forNumber(bidResponse.getCustomRevShare()).equals(FloorEntityType.REMARKETING_FLOOR)) {
                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, "trumpAd"));
                    trumpBids.add(bidResponse);
                }
                // 归因设备的直接胜出给demond端使用 2这个值搞成枚举好点
                if (bidResponse.getAds(0).getAttributionType() == 2) {
                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, "trumpAttri"));
                    trumpBids.add(bidResponse);
                }
                // 品牌的直接放入
//                if (bidResponse.getSourceType() == SourceType.BRAND_VALUE
//                        || FloorEntityType.forNumber(bidResponse.getCustomRevShare()).equals(FloorEntityType.BRAND_FLOOR)) {
//                    brandTrumpBids.add(bidResponse);
//                }
                // dsp or saturn 的部分渠道直接胜出
                if (bidResponse.getSourceType() == SourceType.SATURN_VALUE && TRUMP_DSP_CHANNEL_ID_LIST.get().contains(bidResponse.getAds(0).getSaturnChannelId())) {
                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, "dspTrumpAd"));
                    //dspTrumpBids.add(bidResponse);
                }
                //step 1:set bidding info
                FloorEntityType floorEntityType = FloorEntityType.forNumber(bidResponse.getCustomRevShare());
                Ad firstAd = bidResponse.getAds(0);
                runTimeContext.getBidding().put(floorEntityType, firstAd.getBidPrice());
                // 存在
                if (validBidResponseList.size() >= 2) {
                    //step 2:auction
                    if (bidResponse.getAds(0).getSaturnChannelId().equals(SaturnDsp.TOUTIAO_CHANNEL_ID)) {
                        toutiaoBid = bidResponse;
                    }
                    Ad ad = bidResponse.getAdsList().get(0);
                    // 虚拟竞价
                    if (ad.getBidPrice() > firstPrice) {
                        secondPrice = firstPrice;
                        firstPrice = ad.getBidPrice();

                        secondBidResponse = winBidResponse;
                        winBidResponse = bidResponse;
                    } else {
                        if (ad.getBidPrice() > secondPrice) {
                            secondPrice = ad.getBidPrice();
                            secondBidResponse = bidResponse;
                        }
                    }
                }
            }

            boolean isBaidu = runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get());
            RevenueShareType revenueShareType = RevenueShareType.forNumber(runTimeContext.getPlacementModel().getRevShareType());
            FloorEntityType floorEntityType = FloorEntityType.forNumber(winBidResponse.getCustomRevShare());
            long peDefault = Optional.ofNullable(runTimeContext.getPlacementModel().getPubExpectation()).orElse(0L);
            long pe = peDefault;
            if(null != runTimeContext.getCosmosRevSharePe().get(floorEntityType) && runTimeContext.getCosmosRevSharePe().get(floorEntityType) > 0){
                pe = runTimeContext.getCosmosRevSharePe().get(floorEntityType);
            }
            runTimeContext.setRealRe(runTimeContext.getCosmosRevSharePe().get(floorEntityType));

            boolean isCpc = false;
            long cpcPrice = 0L;
            // 优先计算出需要胜出的
            if (CollectionUtils.isNotEmpty(trumpBids)) {
                //3.remarketing trump广告
                BidResponse winBid = trumpBids.get(0);
                Ad winAd = winBid.getAds(0);
                firstPrice = winAd.getBidPrice();
                if (isBaidu) {
                    cpcPrice = winAd.getBidPriceCpc();
                }
                double adpoolDeduction = winAd.getAdpoolDeduction();
                double netDeduction = winAd.getNetDeduction();
                long floor = runTimeContext.getFloorEntityMap().get(floorEntityType);
                long charge = 0;
                //floor进行8项还原确保在同一维度进行比价获取secondPrice
                BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
                BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
                //包含 pe deduction rev
                BigDecimal floor12 = BigDecimal.valueOf(floor).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
                // 用floor 还原 demand 一价
                BigDecimal floor4 = floor12.multiply(fourDeduction);
                floor = floor4.longValue();
                log.info("placementId {} requestId {}  floor {} floor12 {} floor4 {}",
                        runTimeContext.getPlacementId(), runTimeContext.getRequestId(),
                        floor, floor12, floor4
                );
                //二价
                charge = Math.max(winAd.getSecondBidPrice(), floor);

                Map<ChargeAndPay, Long> trumpChargeAndPay;
                // 交易id
                Pair<Long, String> newPayAndDealId = dynamicPay(runTimeContext, FloorEntityType.REMARKETING_FLOOR, null);
                Long newPe = newPayAndDealId.getLeft();
                // 为了双11手动计算新pe
                String newPeDealId = newPayAndDealId.getRight();
                if (newPe != null) {
                    Map<ChargeAndPay, Long> peMap = calChargeAndPay(revenueShareType, pe, firstPrice, charge, cosmosRevShare, FloorEntityType.REMARKETING_FLOOR, adpoolDeduction, netDeduction, SettleType.forNumber(winAd.getSettleType()), runTimeContext);
                    trumpChargeAndPay = peMap;
                    //原来的pe计算出来的值还是需要保
                    trumpChargeAndPay.put(ChargeAndPay.ORIGIN_PAY, peMap.get(ChargeAndPay.PAY));
                    //替换pay
                    trumpChargeAndPay.put(ChargeAndPay.PAY, newPe);
                    if (StringUtils.isNotBlank(newPeDealId)) {
                        runTimeContext.setNewPeDealId(newPeDealId);
                    }
                } else {
                    if (isBaidu) {
                        trumpChargeAndPay = calChargeAndPayBaidu(cpcPrice, revenueShareType, pe, firstPrice, charge, cosmosRevShare, FloorEntityType.REMARKETING_FLOOR, adpoolDeduction, netDeduction, SettleType.forNumber(winAd.getSettleType()), runTimeContext);
                    } else {
                        trumpChargeAndPay = calChargeAndPay(revenueShareType, pe, firstPrice, charge, cosmosRevShare, FloorEntityType.REMARKETING_FLOOR, adpoolDeduction, netDeduction, SettleType.forNumber(winAd.getSettleType()), runTimeContext);
                    }
                }
                if (trumpChargeAndPay.get(ChargeAndPay.PAY) <= 0L || trumpChargeAndPay.get(ChargeAndPay.CHARGE) <= 0L) {
                    runTimeContext.getDspMap().put(String.valueOf(winBid.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                    throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
                }
                runTimeContext.setChargeAndPayMap(trumpChargeAndPay);
                runTimeContext.setWinBidResponse(winBid);
                runTimeContext.setWinAd(winAd);
                runTimeContext.setFirstPrice(winAd.getBidPrice());
                runTimeContext.setSecondPrice(Math.max(winAd.getSecondBidPrice() + AuctionParam.FEN, floor));
                runTimeContext.getBidding().put(FloorEntityType.REMARKETING_FLOOR, winAd.getBidPrice());
                runTimeContext.setTotalDiscount((float) winAd.getNetDeduction());
                runTimeContext.setAdpoolDeduction(winAd.getAdpoolDeduction());
                runTimeContext.setAdvSettlePrice(trumpChargeAndPay.get(ChargeAndPay.CHARGE));
                runTimeContext.getDspMap().put(String.valueOf(winBid.getSourceType()), DspStaus.AUCTION_WIN_VALUE);
                return Mono.just(new AuctionDataType());
            }
            Ad winAd = winBidResponse.getAds(0);
            //set winBidResponse
            runTimeContext.setWinBidResponse(winBidResponse);
            //set secondBidResponse
            runTimeContext.setSecondBidResponse(secondBidResponse);
            //set winAd
            runTimeContext.setWinAd(winAd);
            firstPrice = winAd.getBidPrice();
            long floor = runTimeContext.getFloorEntityMap().get(floorEntityType);
            double adpoolDeduction = winAd.getAdpoolDeduction();
            double netDeduction = winAd.getNetDeduction();
            if (isBaidu) {
                cpcPrice = winAd.getBidPriceCpc();
            }
            //floor进行8项还原确保在同一维度进行比价获取secondPrice
//            if (isNewDucation(runTimeContext.getPlacementId())) {
            BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
            BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
            // 用floor 还原 pay+deduction+inmobi收益
            BigDecimal floor12 = BigDecimal.valueOf(floor).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
            // 用floor 还原 广告主一价
            BigDecimal floor4 = floor12.multiply(fourDeduction);
            floor = floor4.longValue();
//            }
            //如果有第二广告胜出 用第二广告一价和floor还原各项扣除比较，较大的为二价。若无第二广告胜出,用第一广告胜出二价和floor还原各项对比，较大的为二价
            secondPrice = (secondBidResponse != null)
                    ? Math.max(secondBidResponse.getAds(0).getBidPrice(), Math.max(winAd.getSecondBidPrice(), floor))
                    : Math.max(winAd.getSecondBidPrice(), floor);
            runTimeContext.setFirstPrice(firstPrice);
            runTimeContext.setSecondPrice(secondPrice);

            Integer accuracy = Optional.ofNullable(runTimeContext.getPlacementModel().getAccuracy()).orElse(95);

            Map<ChargeAndPay, Long> chargeAndPayLongMap = chargeAdvertiserAndPayPublisher(cpcPrice, revenueShareType, pe, accuracy, floorEntityType,
                    cosmosRevShare, secondPrice, firstPrice, runTimeContext, SettleType.forNumber(winAd.getSettleType()), adpoolDeduction, netDeduction, winAd.getSaturnChannelId());
            runTimeContext.setChargeAndPayMap(chargeAndPayLongMap);
            runTimeContext.setTotalDiscount((float) winAd.getNetDeduction());
            runTimeContext.setAdpoolDeduction(winAd.getAdpoolDeduction());
            runTimeContext.setAdvSettlePrice(chargeAndPayLongMap.get(ChargeAndPay.CHARGE));

            if (!winBidResponse.getSaturnDsp().equals(SaturnDsp.TOUTIAO) && toutiaoBid != null) {
                //toutiao auction fail
                buildTouTiaoLostNotice(runTimeContext, toutiaoBid);
            }
            if (chargeAndPayLongMap.get(ChargeAndPay.PAY) <= 0L || chargeAndPayLongMap.get(ChargeAndPay.CHARGE) <= 0L) {
                runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
            }
            if (winBidResponse.getAdsList() != null && winBidResponse.getAdsList().get(0).getBidPriceCpc() > 0L && chargeAndPayLongMap.get(ChargeAndPay.PAY) >= DynamicIntegerConfig.CPC_PRICE_UP.get().longValue()&& DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getPlacementModel().getAccountId())) {
                runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                log.info("requestId {} price too high", runTimeContext.getRequestId());
                throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
            }
            runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.AUCTION_WIN_VALUE);
            return Mono.just(new AuctionDataType());
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void buildTouTiaoLostNotice(RunTimeContext runTimeContext, BidResponse toutiaoBid) {
        TouTiaoLostNotice toutiaoLostNotice = new TouTiaoLostNotice();
        toutiaoLostNotice.setName(SaturnDsp.TOUTIAO_LOST_NAME);
        toutiaoLostNotice.setRequestId(runTimeContext.getRequestId());
        toutiaoLostNotice.setRit(String.valueOf(runTimeContext.getPlacementId()));
        toutiaoLostNotice.setCode(TouTiaoLostNotice.LostCode.AUCTION_FAIL.getCode());
        toutiaoLostNotice.setReason(TouTiaoLostNotice.LostCode.AUCTION_FAIL.getDesc());
        toutiaoLostNotice.setTmax(200);
        if (CollectionUtils.isNotEmpty(toutiaoBid.getAdsList().get(0).getCreative().getImpUrlList())) {
            String impUrl = toutiaoBid.getAdsList().get(0).getCreative().getImpUrlList().get(0);
            String extra = URLRequest(impUrl).getOrDefault("extra", "");
            toutiaoLostNotice.setExtra(extra);
        }
        WinNotice.Builder lostNoticeBuilder = WinNotice.newBuilder();
        lostNoticeBuilder.setEvent(WinNotice.Event.LOSE);
        lostNoticeBuilder.setMethod(WinNotice.Method.POST);
        lostNoticeBuilder.setUrl(SaturnDsp.TOUTIAO_LOST_NOTICE_URL);
        lostNoticeBuilder.setBody(ObjectMapperUtils.toJSON(toutiaoLostNotice));
        lostNoticeBuilder.setSaturnDsp(SaturnDsp.TOUTIAO);
        runTimeContext.getLostNoticeList().put(SaturnDsp.TOUTIAO, Collections.singletonList(lostNoticeBuilder.build()));
    }

    private Map<ChargeAndPay, Long> chargeAdvertiserAndPayPublisher(long cpcPrice, RevenueShareType revenueShareType, Long pe, Integer accuracy, FloorEntityType floorEntityType,
                                                                    Map<FloorEntityType, BigDecimal> cosmosRevShare, Long secondPrice, Long firstPrice, RunTimeContext runTimeContext,
                                                                    SettleType settleType, double adpoolDeduction, double netDeduction, String saturnChannelId) {
        Map<ChargeAndPay, Long> map;
        switch (floorEntityType) {
            case PERF_FLOOR:
            case BRAND_FLOOR:
            case REMARKETING_FLOOR: {
                Pair<Long, String> newPayAndDealId = dynamicPay(runTimeContext, floorEntityType, null);
                Long newPe = newPayAndDealId.getLeft();
                String newPeDealId = newPayAndDealId.getRight();
                if (newPe != null) {
                    Map<ChargeAndPay, Long> peMap = getPerfAccount(cpcPrice, revenueShareType, pe, firstPrice, secondPrice, cosmosRevShare, floorEntityType, adpoolDeduction, netDeduction, settleType, runTimeContext);
                    map = peMap;
                    //原来的pe计算出来的值还是需要保
                    map.put(ChargeAndPay.ORIGIN_PAY, peMap.get(ChargeAndPay.PAY));
                    //替换pay
                    map.put(ChargeAndPay.PAY, newPe);
                    if (StringUtils.isNotBlank(newPeDealId)) {
                        runTimeContext.setNewPeDealId(newPeDealId);
                    }
                } else {
                    map = getPerfAccount(cpcPrice, revenueShareType, pe, firstPrice, secondPrice, cosmosRevShare, floorEntityType, adpoolDeduction, netDeduction, settleType, runTimeContext);
                }
                break;
            }
            default: {
                Pair<Long, String> newPayAndDealId = dynamicPay(runTimeContext, floorEntityType, saturnChannelId);
                Long newPe = newPayAndDealId.getLeft();
                String newPeDealId = newPayAndDealId.getRight();
                if (newPe != null) {
                    Map<ChargeAndPay, Long> peMap = getSaturnAccount(cpcPrice, revenueShareType, pe, accuracy, floorEntityType, cosmosRevShare, secondPrice, firstPrice,
                            runTimeContext, settleType, adpoolDeduction, netDeduction, saturnChannelId);
                    map = peMap;
                    //原来的pe计算出来的值还是需要保
                    map.put(ChargeAndPay.ORIGIN_PAY, peMap.get(ChargeAndPay.PAY));
                    //替换pay
                    map.put(ChargeAndPay.PAY, newPe);
                    if (StringUtils.isNotBlank(newPeDealId)) {
                        runTimeContext.setNewPeDealId(newPeDealId);
                    }
                } else {
                    map = getSaturnAccount(cpcPrice, revenueShareType, pe, accuracy, floorEntityType, cosmosRevShare, secondPrice, firstPrice,
                            runTimeContext, settleType, adpoolDeduction, netDeduction, saturnChannelId);
                }
                break;
            }
        }
        if (revenueShareType.equals(RevenueShareType.MAX_REVENUE_SHARE)) {
            map.put(ChargeAndPay.PAY, pe);
        }
        return map;
    }

    private Map<ChargeAndPay, Long> getPerfAccount(long cpcPrice, RevenueShareType revenueShareType, Long pe, Long firstPrice, Long secondPrice, Map<FloorEntityType, BigDecimal> cosmosRevShare,
                                                   FloorEntityType floorEntityType, double adpoolDeduction, double advTotalDiscount, SettleType settleType, RunTimeContext runTimeContext) {
//        long baiduPrice = runTimeContext.getBaiduBrice();
        boolean isBaidu = runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get());
        if (isBaidu) {
            return getPerfAccountBaidu(cpcPrice, revenueShareType, pe, firstPrice, secondPrice, cosmosRevShare, floorEntityType, adpoolDeduction, advTotalDiscount, settleType, runTimeContext);
        }
        return calChargeAndPay(revenueShareType, pe, firstPrice, secondPrice, cosmosRevShare, floorEntityType, adpoolDeduction, advTotalDiscount, settleType, runTimeContext);
    }

    // 除了wade 谁也不要动此方法马上准备重写
    private Map<ChargeAndPay, Long> getSaturnAccount(long cpcPrice, RevenueShareType revenueShareType, Long pe, Integer accuracy, FloorEntityType floorEntityType,
                                                     Map<FloorEntityType, BigDecimal> cosmosRevShare, Long secondPrice, Long firstPrice, RunTimeContext runTimeContext,
                                                     SettleType settleType, double adpoolDeduction, double netDeduction, String saturnChannelId) {
        boolean isBaidu = runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get());
        if (isBaidu) {
            return getSaturnAccountBaidu(cpcPrice, revenueShareType, pe, accuracy, floorEntityType, cosmosRevShare, secondPrice, firstPrice,
                    runTimeContext, settleType, adpoolDeduction, netDeduction, saturnChannelId);
        }
        int count = DynamicIntegerConfig.DEFAULT_MARGIN_BAND_COUNT.get() - 1; //=2
        List<BigDecimal> vbRevShares = new ArrayList<>();
        BigDecimal defaultMinRevShare = new BigDecimal(DynamicStringConfig.DEFAULT_MIN_REV_SHARE.get());
        BigDecimal defaultMaxRevShare = new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get());

        vbRevShares.add(defaultMinRevShare);
        for (int i = 1; i < count; i++) {
            BigDecimal vbRevShare = defaultMinRevShare.add(((defaultMaxRevShare.subtract(defaultMinRevShare)).multiply(new BigDecimal(i))).divide(new BigDecimal(count)));
            vbRevShares.add(vbRevShare);
        }
        vbRevShares.add(defaultMaxRevShare);

        List<BigDecimal> vbs = vbRevShares.stream()
                .map(x -> BigDecimal.valueOf(pe).divide(x, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP))
                .collect(Collectors.toList());

        List<BigDecimal> noisy = calculateMarginBandsWithNoise(vbs, runTimeContext); // 从大到小排列
        LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} noisy {}", runTimeContext.getPlacementId(),ObjectMapperUtils.toJSON(noisy));
        List<BigDecimal> accuracyList = calculateAccuracy(accuracy, pe); // 从大到小排列
        LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} accuracyList {}", runTimeContext.getPlacementId(),ObjectMapperUtils.toJSON(accuracyList));

        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06

        BigDecimal rawSecondBidPrice = BigDecimal.valueOf(secondPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal rawFirstBidPrice = BigDecimal.valueOf(firstPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);

        BigDecimal charge = rawSecondBidPrice;

        // 1:判断广告位是否在阿波罗使用新逻辑
        // 2:判断floorPrice 和firstPrist 谁大。暂时动态-（1，3）分
        long floorPrice = 0;
        double seedPe = ThreadLocalRandom.current().nextDouble(0.98, 1.01);
        long randomPe = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(seedPe)).longValue();

        if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
            double seed = ThreadLocalRandom.current().nextDouble(0.92, 0.98);
            floorPrice = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(seed)).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP).longValue();
            // 判断floor还原12项之后，是否大于dsp的扣完4项的price TODO 判断dsp的真实一价
            if (floorPrice > firstPrice) {
                log.info("requestId={} floorPrice big than firstPrice", runTimeContext.getRequestId());
                floorPrice = firstPrice - AuctionParam.FEN * (new Random().nextInt(3));
            }
        }
        log.info("requestId={} floorPrice={} twelveDeduction={} pe={} charge={}",
                runTimeContext.getRequestId(), floorPrice, twelveDeduction, pe, charge);


        long pay = accuracyList.get(accuracyList.size() - 1).longValue();
        LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} pay {}", runTimeContext.getPlacementId(),pay);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (settleType.equals(SettleType.FIRST)) {
            charge = rawFirstBidPrice;
            BigDecimal rawChargeWithoutCost = charge.multiply(twelveDeduction);
            if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                // 直接给pe即可
                pay = randomPe;
            } else {
                // 临时补丁马上修改
                if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
                    pay = (rawChargeWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();
                } else {
                    pay = randomPe;
                }
            }

        } else {
            for (int i = 0; i < noisy.size(); i++) {
                if (firstPrice > noisy.get(i).longValue()) {
                    long bid = Math.max(floorPrice, Math.max(rawSecondBidPrice.longValue(), noisy.get(i).longValue()));
                    LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} bid {}", runTimeContext.getPlacementId(),bid);
                    if (WITHOUT_VB_CHANNEL_WHITE_LIST.get().contains(saturnChannelId)
                            && random.nextInt(100) <= NO_VB_PROPORTION.get()) {
                        bid = Math.max(floorPrice, rawSecondBidPrice.longValue());
                        LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} max bid {}", runTimeContext.getPlacementId(),bid);
                    }
                    charge = BigDecimal.valueOf(bid);
                    BigDecimal rawChargeWithoutCost = charge.multiply(twelveDeduction);
                    LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} charge {} rawChargeWithoutCost {}", runTimeContext.getPlacementId(),charge,rawChargeWithoutCost);
                    if (rawChargeWithoutCost.compareTo(BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(0.95))) < 0
                            && revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                        PerfUtils.perf("rawChargeWithoutCost.less.than.pe");
                        pay = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(0.95)).longValue();
                    } else {
                        if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                            pay = accuracyList.get(i).longValue();
                        } else {
                            if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
                                pay = (rawChargeWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();

                            } else {
                                // 临时补丁马上修改
                                pay = randomPe;
                            }
                        }
                    }
                    break;
                }
            }
        }
        if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
            Long upperBound = BigDecimal.valueOf(pe).multiply(AuctionParam.ONE_POINT_ONE).longValue();
            Long lowerBound = BigDecimal.valueOf(pe).multiply(AuctionParam.POINT_NINE_FIVE).longValue();
            if (pay > upperBound) {
                pay = upperBound;
            } else if (pay < lowerBound) {
                pay = lowerBound;
            }
        }
        //PD 模式
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
        }
        Map<ChargeAndPay, Long> map = new HashMap<>();
        map.put(ChargeAndPay.CHARGE, charge.longValue() + AuctionParam.FEN);
        map.put(ChargeAndPay.PAY, pay);
        LogUtils.logInfo(runTimeContext.getRequestId(),"placementId={} charge and pay {} ", runTimeContext.getPlacementId(), ObjectMapperUtils.toJSON(map));
        return map;
    }

    private List<BigDecimal> calculateAccuracy(Integer accuracy, Long pe) {
        List<BigDecimal> accuracyInterval = new ArrayList<>();
        List<BigDecimal> accuracyRandom = new ArrayList<>();
        BigDecimal bigAccuracy = BigDecimal.valueOf(accuracy);
        int defaultMarginBandCount = DynamicIntegerConfig.DEFAULT_MARGIN_BAND_COUNT.get() + 1;
        BigDecimal bigMerginBandCount = BigDecimal.valueOf(defaultMarginBandCount);
        for (int i = 0; i < defaultMarginBandCount; i++) {
            accuracyInterval.add(((AuctionParam.ONE_HUNDRED_AND_TEN_AND_ONE.subtract(bigAccuracy)).divide(bigMerginBandCount, RuntimeConstant.BIGDECIMAL_SCALE).multiply(BigDecimal.valueOf(i)).add(bigAccuracy)));
        }
        accuracyInterval.add(AuctionParam.ONE_HUNDRED_AND_TEN_AND_ONE);
        for (int i = 1; i < accuracyInterval.size(); i++) {
            accuracyRandom.add(new BigDecimal(ThreadLocalRandom.current().nextLong(accuracyInterval.get(i - 1).multiply(BigDecimal.valueOf(pe)).longValue(), accuracyInterval.get(i).multiply(BigDecimal.valueOf(pe)).longValue())));
        }
        ArrayList<BigDecimal> reverseAccuracy = new ArrayList<>();
        for (int i = accuracyRandom.size() - 1; i >= 0; i--) {
            reverseAccuracy.add(accuracyRandom.get(i).divide(AuctionParam.ONE_HUNDRED, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP));
        }
        return reverseAccuracy;
    }

    private static List<BigDecimal> calculateMarginBandsWithNoise(List<BigDecimal> vbs, RunTimeContext runTimeContext) {
        BigDecimal maxDelta = new BigDecimal(DynamicStringConfig.DEFAULT_MAX_DELTA.get());
        BigDecimal noiseLevel = new BigDecimal(DynamicStringConfig.DEFAULT_NOISE_LEVEL.get());

        Map<Integer, BigDecimal> defaultDeltaHourOfDay = DynamicMapConfig.DEFAULT_DELTA_HOUR_OF_DAY.get()
                .entrySet().stream().collect(Collectors.toMap(entry -> Integer.valueOf(entry.getKey().toString()), entry -> new BigDecimal(entry.getValue().toString()), (o1, o2) -> o1));
        LocalDateTime currentDate = LocalDateTime.now();
        BigDecimal deltaHourOfDay = defaultDeltaHourOfDay.get(currentDate.getHour());

        String bundleId = runTimeContext.getAppModel().getBundleId();
        String bundleHour = bundleId + currentDate.format(AuctionParam.apMarginBandDateTimeFormatter);

        BigDecimal bundleHourHash = getCRCHash(bundleHour);

        BigDecimal deltaBundleHour = (bundleHourHash.subtract(AuctionParam.FIFTY)).multiply(AuctionParam.TWO);

        BigDecimal deltaRandom = AuctionParam.ZERO;
        if (noiseLevel.intValue() > 1) {
            BigDecimal random = new BigDecimal(ThreadLocalRandom.current().nextInt(0, noiseLevel.intValue()));
            deltaRandom = AuctionParam.NEGATIVE_ONE_HUNDRED.add(random.multiply(AuctionParam.TWO_HUNDRED).divide(noiseLevel.subtract(AuctionParam.ONE)));
        }

        BigDecimal deltaPercent = (deltaHourOfDay.add(deltaBundleHour).add(deltaRandom)).divide(AuctionParam.THREE, RuntimeConstant.BIGDECIMAL_SCALE);
        BigDecimal marginBandNoise = deltaPercent.divide(AuctionParam.ONE_HUNDRED, RuntimeConstant.BIGDECIMAL_SCALE);

        List<BigDecimal> noisy = new ArrayList<>();
        noisy.add(vbs.get(0));
        for (int i = 1; i < vbs.size(); i++) {
            BigDecimal deltaCPM = (vbs.get(i).subtract(vbs.get(i - 1))).multiply(marginBandNoise).multiply(maxDelta);
            BigDecimal randomizedCurrentBandCPM = vbs.get(i).add(deltaCPM);
            noisy.add(randomizedCurrentBandCPM);
        }
        return noisy;
    }

    private static BigDecimal getCRCHash(String str) {
        CRC32 crc = new CRC32();
        crc.update(str.getBytes());
        return new BigDecimal((int) (crc.getValue() % 100));
    }


    /**
     * @param revenueShareType
     * @param pe
     * @param secondPrice
     * @param cosmosRevShare
     * @param floorEntityType
     * @param adpoolDeduction  4项折扣
     * @param netDeduction     12项折扣项的总和
     * @return
     */
    private Map<ChargeAndPay, Long> calChargeAndPay(RevenueShareType revenueShareType, Long pe, Long firstPrice, Long secondPrice, Map<FloorEntityType, BigDecimal> cosmosRevShare, FloorEntityType floorEntityType,
                                                    double adpoolDeduction, double netDeduction, SettleType settleType, RunTimeContext runTimeContext) {
        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
        //一价还原广告主出价
        BigDecimal rawFirstBidPrice = BigDecimal.valueOf(firstPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);

        BigDecimal bidPrice = BigDecimal.valueOf(secondPrice);

        //calculate raw bidPrice  二价还原广告主出价
        BigDecimal rawBidPrice = bidPrice.divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        //calculate raw bidPrice without cost  二价 pay + inmobi收益
        BigDecimal rawBidWithoutCost = rawBidPrice.multiply(twelveDeduction);

        Long pay;
        Map<ChargeAndPay, Long> map = new HashMap<>();
        BigDecimal peBig = BigDecimal.valueOf(pe);
        BigDecimal peBig95 = peBig.multiply(BigDecimal.valueOf(0.95));

        if (rawBidWithoutCost.compareTo(BigDecimal.valueOf(pe)) < 0
                && revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
            PerfUtils.perf("rawChargeWithoutCost.less.than.pe");
            pay = peBig95.longValue();

//            if (rawBidWithoutCost.compareTo(peBig95) >= 0) {
//                pay = peBig95.longValue();
//            } else {
//                pay = rawBidWithoutCost.longValue();
//            }
            //raw bidPrice
            if (settleType.equals(SettleType.FIRST)) {
                map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
            } else {
                map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
            }
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        //PD 模式分成
        if(revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)){
            pay = pe;
            if (settleType.equals(SettleType.FIRST)) {
                map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
            } else {
                map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
            }
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        //使用rawBidWithoutCost计算pay
        pay = (rawBidWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();
        if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
            Long upperBound = peBig.multiply(AuctionParam.ONE_POINT_ONE).longValue();
            Long lowerBound = peBig.multiply(AuctionParam.POINT_NINE_FIVE).longValue();
            if (pay > upperBound) {
                pay = upperBound;
            } else if (pay < lowerBound) {
                pay = lowerBound;
            }
        }
        // 临时补丁后续修改
        if (cosmosRevShare.get(floorEntityType).longValue() > 1) {
            pay = peBig.longValue();
        }
        //raw bidPrice
        if (settleType.equals(SettleType.FIRST)) {
            map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
        } else {
            map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
        }
        map.put(ChargeAndPay.PAY, pay);
        return map;
    }

    /**
     * 双十一为了拿更多好媒体的量，手动去抬高结算价格
     *
     * @param runTimeContext
     * @param floorEntityType
     * @param dspChannelId
     * @return
     */
    private Pair<Long, String> dynamicPay(RunTimeContext runTimeContext, FloorEntityType floorEntityType, String dspChannelId) {
        Long newPay = null;
        String newPayDealId = "";
        String channelId;
        switch (floorEntityType) {
            case PERF_FLOOR:
                channelId = String.valueOf(SourceType.PERF_VALUE);
                break;
            case BRAND_FLOOR:
                channelId = String.valueOf(SourceType.BRAND_VALUE);
                break;
            case REMARKETING_FLOOR:
                channelId = String.valueOf(SourceType.REMARKETING_VALUE);
                break;
            default:
                channelId = String.valueOf(SourceType.SATURN_VALUE);
                break;
        }
        if (CollectionUtils.isNotEmpty(runTimeContext.getPlacementModel().getRtbPes())) {
            //为了双十一,手动抬高dsp媒体PE,拿到更多的量
            for (PlacementRtbPe rtbPe : runTimeContext.getPlacementModel().getRtbPes()) {
                if (rtbPe != null) {
                    long now = System.currentTimeMillis();
                    if (StringUtils.isNotBlank(rtbPe.getChannelId())
                            && StringUtils.isNotBlank(dspChannelId)
                            && rtbPe.getChannelId().equalsIgnoreCase(dspChannelId)) {
                        List<Map<String, Long>> validTimeIntervals = rtbPe.getValidTimeInMs();
                        for (Map<String, Long> timeMap : validTimeIntervals) {
                            Long beginTime = timeMap.getOrDefault("start", null);
                            Long endTime = timeMap.getOrDefault("end", null);
                            if (beginTime != null && endTime != null && now >= beginTime && now <= endTime) {
                                newPay = rtbPe.getPubExpectation();
                                if (StringUtils.isNotBlank(rtbPe.getDealId())) {
                                    newPayDealId = rtbPe.getDealId();
                                }
                                break;
                            }
                        }
                        if (newPay != null) {
                            break;
                        }
                    } else if (StringUtils.isNotBlank(rtbPe.getChannelId())
                            && rtbPe.getChannelId().equalsIgnoreCase(channelId)) {
                        List<Map<String, Long>> validTimeIntervals = rtbPe.getValidTimeInMs();
                        for (Map<String, Long> timeMap : validTimeIntervals) {
                            Long beginTime = timeMap.getOrDefault("start", null);
                            Long endTime = timeMap.getOrDefault("end", null);
                            if (beginTime != null && endTime != null && now >= beginTime && now <= endTime) {
                                newPay = rtbPe.getPubExpectation();
                                if (StringUtils.isNotBlank(rtbPe.getDealId())) {
                                    newPayDealId = rtbPe.getDealId();
                                }
                                break;
                            }
                        }
                        if (newPay != null) {
                            break;
                        }
                    }
                }
            }
        }
        if (newPay != null || StringUtils.isNotBlank(newPayDealId)) {
//            log.info("placementId={} newPay={} newPayDealId={} floorEntityType={}", runTimeContext.getPlacementId(), newPay, newPayDealId, floorEntityType);
        }
        return Pair.of(newPay, newPayDealId);
    }


    private Map<ChargeAndPay, Long> getPerfAccountBaidu(long cpcPrice, RevenueShareType revenueShareType, Long pe, Long firstPrice, Long secondPrice, Map<FloorEntityType, BigDecimal> cosmosRevShare,
                                                        FloorEntityType floorEntityType, double adpoolDeduction, double advTotalDiscount, SettleType settleType, RunTimeContext runTimeContext) {
        return calChargeAndPayBaidu(cpcPrice, revenueShareType, pe, firstPrice, secondPrice, cosmosRevShare, floorEntityType, adpoolDeduction, advTotalDiscount, settleType, runTimeContext);
    }

    // 除了wade 谁也不要动此方法马上准备重写
    private Map<ChargeAndPay, Long> getSaturnAccountBaidu(long cpcPrice, RevenueShareType revenueShareType, Long pe, Integer accuracy, FloorEntityType floorEntityType,
                                                          Map<FloorEntityType, BigDecimal> cosmosRevShare, Long secondPrice, Long firstPrice, RunTimeContext runTimeContext,
                                                          SettleType settleType, double adpoolDeduction, double netDeduction, String saturnChannelId) {
        int count = DynamicIntegerConfig.DEFAULT_MARGIN_BAND_COUNT.get() - 1; //=2
        List<BigDecimal> vbRevShares = new ArrayList<>();
        BigDecimal defaultMinRevShare = new BigDecimal(DynamicStringConfig.DEFAULT_MIN_REV_SHARE.get());
        BigDecimal defaultMaxRevShare = new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get());
        long baiduPrice = runTimeContext.getBaiduBrice();

        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
        if (cpcPrice > 0L) {
            BigDecimal cpcPrice4 = BigDecimal.valueOf(cpcPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
            long pay = cpcPrice4.multiply(twelveDeduction).longValue();
            // 比例分成
            long cpcCharge = (cpcPrice4.multiply(cosmosRevShare.get(floorEntityType))).longValue();
            Map<ChargeAndPay, Long> map = new HashMap<>();
            map.put(ChargeAndPay.CHARGE, cpcCharge + AuctionParam.FEN);
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }
        boolean isBaidu = runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get());
        vbRevShares.add(defaultMinRevShare);
        for (int i = 1; i < count; i++) {
            BigDecimal vbRevShare = defaultMinRevShare.add(((defaultMaxRevShare.subtract(defaultMinRevShare)).multiply(new BigDecimal(i))).divide(new BigDecimal(count)));
            vbRevShares.add(vbRevShare);
        }
        vbRevShares.add(defaultMaxRevShare);
        List<BigDecimal> vbs = Lists.newArrayList();
        if (isBaidu && baiduPrice > 0L) {
            vbs = vbRevShares.stream()
                    .map(x -> BigDecimal.valueOf(baiduPrice).divide(x, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        } else {
            vbs = vbRevShares.stream()
                    .map(x -> BigDecimal.valueOf(pe).divide(x, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        }

        List<BigDecimal> noisy = calculateMarginBandsWithNoise(vbs, runTimeContext); // 从大到小排列
        List<BigDecimal> accuracyList = calculateAccuracy(accuracy, pe); // 从大到小排列

        BigDecimal rawSecondBidPrice = BigDecimal.valueOf(secondPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal rawFirstBidPrice = BigDecimal.valueOf(firstPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal charge = rawSecondBidPrice;

        long floorPrice = 0;
        // 1:判断广告位是否在阿波罗使用新逻辑
        // 2:判断floorPrice 和firstPrist 谁大。暂时动态-（1，3）分
        double seedPe = ThreadLocalRandom.current().nextDouble(0.98, 1.01);
        long randomPe = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(seedPe)).longValue();

        if (isBaidu && baiduPrice > 0L) {
            floorPrice = 0L;
        }

        if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
            double seed = ThreadLocalRandom.current().nextDouble(0.92, 0.98);
            floorPrice = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(seed)).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP).longValue();
            if (floorPrice > firstPrice) {
                floorPrice = firstPrice - AuctionParam.FEN * (new Random().nextInt(3));
            }
        } else {
        }


        long pay = accuracyList.get(accuracyList.size() - 1).longValue();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (settleType.equals(SettleType.FIRST)) {
            charge = rawFirstBidPrice;
            BigDecimal rawChargeWithoutCost = charge.multiply(twelveDeduction);
            if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                // 直接给pe即可
                pay = randomPe;
                if (isBaidu && baiduPrice > 0L) {
                    pay = runTimeContext.getBaiduBrice();
                }
            } else {
                // 临时补丁马上修改
                if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
                    pay = (rawChargeWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();
                } else {
                    pay = randomPe;
                    if (isBaidu && baiduPrice > 0L) {
                        pay = runTimeContext.getBaiduBrice();
                    }
                }
            }

        } else {
            for (int i = 0; i < noisy.size(); i++) {
                if (firstPrice > noisy.get(i).longValue()) {
                    long bid = Math.max(floorPrice, Math.max(rawSecondBidPrice.longValue(), noisy.get(i).longValue()));
                    if (WITHOUT_VB_CHANNEL_WHITE_LIST.get().contains(saturnChannelId)
                            && random.nextInt(100) <= NO_VB_PROPORTION.get()) {
                        bid = Math.max(floorPrice, rawSecondBidPrice.longValue());
                    }

                    charge = BigDecimal.valueOf(bid);
                    BigDecimal rawChargeWithoutCost = charge.multiply(twelveDeduction);
                    if (rawChargeWithoutCost.compareTo(BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(0.95))) < 0
                            && revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                        PerfUtils.perf("rawChargeWithoutCost.less.than.pe");
                        if (isBaidu && baiduPrice > 0L) {
                        } else {
                            pay = BigDecimal.valueOf(pe).multiply(BigDecimal.valueOf(0.95)).longValue();
                        }

                    } else {
                        if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                            pay = accuracyList.get(i).longValue();
                        } else {
                            if (cosmosRevShare.get(floorEntityType).longValue() < 1) {
                                pay = (rawChargeWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();

                            } else {
                                // 临时补丁马上修改
                                pay = randomPe;
                                if (isBaidu && baiduPrice > 0L) {
                                    pay = runTimeContext.getBaiduBrice();
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
        if (isBaidu && baiduPrice > 0L) {

        } else {
            if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                Long upperBound = BigDecimal.valueOf(pe).multiply(AuctionParam.ONE_POINT_ONE).longValue();
                Long lowerBound = BigDecimal.valueOf(pe).multiply(AuctionParam.POINT_NINE_FIVE).longValue();
                if (pay > upperBound) {
                    pay = upperBound;
                } else if (pay < lowerBound) {
                    pay = lowerBound;
                }
            }
        }
        //PD 模式
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
        }
        Map<ChargeAndPay, Long> map = new HashMap<>();
        map.put(ChargeAndPay.CHARGE, charge.longValue() + AuctionParam.FEN);
        map.put(ChargeAndPay.PAY, pay);
        return map;
    }

    private Map<ChargeAndPay, Long> calChargeAndPayBaidu(long cpcPrice, RevenueShareType revenueShareType, Long pe, Long firstPrice, Long secondPrice, Map<FloorEntityType, BigDecimal> cosmosRevShare, FloorEntityType floorEntityType,
                                                         double adpoolDeduction, double netDeduction, SettleType settleType, RunTimeContext runTimeContext) {
        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
        BigDecimal rawFirstBidPrice = BigDecimal.valueOf(firstPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);

        //4项折扣还原
        BigDecimal rawBidPrice = BigDecimal.valueOf(secondPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        //12项折扣打折 * 分成比例为给开发者的钱
        BigDecimal rawBidWithoutCost = rawBidPrice.multiply(twelveDeduction);

        boolean isBaidu = DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getPlacementModel().getAccountId());
        long baiduPrice = runTimeContext.getBaiduBrice();
        if (cpcPrice > 0L) {
            BigDecimal cpcPrice4 = BigDecimal.valueOf(cpcPrice).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
            long pay = cpcPrice4.multiply(twelveDeduction).longValue();
            // 按照ecpm的出价计算方式
            long cpcCharge = (cpcPrice4.multiply(cosmosRevShare.get(floorEntityType))).longValue();
            //raw bidPrice
            Map<ChargeAndPay, Long> map = new HashMap<>();

            if (settleType.equals(SettleType.FIRST)) {
                map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
            } else {
                map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
            }
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }
        Long pay;
        Map<ChargeAndPay, Long> map = new HashMap<>();
        BigDecimal peBig = BigDecimal.valueOf(pe);
        BigDecimal peBig95 = peBig.multiply(BigDecimal.valueOf(0.95));

        // todo 此处应该把百度排除，事情逐渐复杂了起来😄😄😄，何时重构？
        // 策略+模板模式嘛？我承认都是折扣惹得祸。再议，再议！
        if (isBaidu && baiduPrice > 0L) {

        } else {
            if (rawBidWithoutCost.compareTo(BigDecimal.valueOf(pe)) < 0
                    && revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                PerfUtils.perf("rawChargeWithoutCost.less.than.pe");
                pay = peBig95.longValue();
                //raw bidPrice
                if (settleType.equals(SettleType.FIRST)) {
                    map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
                } else {
                    map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
                }
                map.put(ChargeAndPay.PAY, pay);
                return map;
            }
        }

        //PD 模式
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
            if (settleType.equals(SettleType.FIRST)) {
                map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
            } else {
                map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
            }
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        //使用rawBidWithoutCost计算pay
        pay = (rawBidWithoutCost.multiply(cosmosRevShare.get(floorEntityType))).longValue();
        if (isBaidu && baiduPrice > 0L) {

        } else {
            if (revenueShareType.equals(RevenueShareType.AUTO_PILOT)) {
                Long upperBound = peBig.multiply(AuctionParam.ONE_POINT_ONE).longValue();
                Long lowerBound = peBig.multiply(AuctionParam.POINT_NINE_FIVE).longValue();
                if (pay > upperBound) {
                    pay = upperBound;
                } else if (pay < lowerBound) {
                    pay = lowerBound;
                }
            }
        }
        // 临时补丁后续修改
        if (cosmosRevShare.get(floorEntityType).longValue() > 1) {
            if (isBaidu && baiduPrice > 0L) {
                pay = baiduPrice;
            } else {
                pay = peBig.longValue();
            }
        }
        //raw bidPrice
        if (settleType.equals(SettleType.FIRST)) {
            map.put(ChargeAndPay.CHARGE, rawFirstBidPrice.longValue());
        } else {
            map.put(ChargeAndPay.CHARGE, rawBidPrice.longValue());
        }
        map.put(ChargeAndPay.PAY, pay);
        return map;
    }

    public static void main(String[] args) {
//        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.09).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
//        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.3175).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
//        // 用floor 还原 pay+deduction+inmobi收益
//        BigDecimal floor12 = BigDecimal.valueOf(950).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
//        // 用floor 还原 广告主一价
//        BigDecimal floor4 = floor12.multiply(fourDeduction);
//        long l = floor4.longValue();
//        System.out.println(l);

        float adpoolDeduction = 0.06f;
        float advTotalDiscount = 0.2725f;
        long floor = 843;
        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(advTotalDiscount).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
        // 用floor 还原 pay+deduction+inmobi收益
        BigDecimal floor12 = BigDecimal.valueOf(floor).divide(twelveDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        // 用floor 还原 广告主一价
        BigDecimal floor4 = floor12.multiply(fourDeduction);
        long l = floor4.longValue();
        System.out.println(l);

        //calculate raw bidPrice  二价还原广告主出价
        BigDecimal rawBidPrice = floor4.divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
        //calculate raw bidPrice without cost  二价 pay + inmobi收益
        BigDecimal rawBidWithoutCost = rawBidPrice.multiply(twelveDeduction);
        System.out.println(rawBidWithoutCost);


//        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.09).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
//        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.3175).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
//
//        //4项折扣还原
//        BigDecimal rawBidPrice = BigDecimal.valueOf(1241).divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
//        //12项折扣打折 * 分成比例为给开发者的钱
//        BigDecimal rawBidWithoutCost = rawBidPrice.multiply(twelveDeduction);
//        System.out.println(rawBidWithoutCost);

    }


}