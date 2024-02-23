package com.inmobi.supply.sun.service.postProc.auction.module;

import cn.inmobi.pb.api.*;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementRtbPe;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
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

import java.math.BigDecimal;
import java.util.*;

import static com.inmobi.supply.sun.constant.AuctionParam.ONE_POINT_ONE;

@Service
@Slf4j
public class AuctionMarketPlaceResponse implements ModuleWorker<AuctionDataType> {
    @Override
    public Mono<AuctionDataType> processMono(RunTimeContext runTimeContext) {
        try {
            List<BidResponse> validBidResponseList = runTimeContext.getValidBidResponseList();
            if (CollectionUtils.isEmpty(validBidResponseList)) {
                throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
            }
            List<BidResponse> trumpBids = new ArrayList<>();
            // remarketing 直接胜出
            for (BidResponse bidResponse : validBidResponseList) {
                /**
                 * 各个渠道的填充监控
                 */
                PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, SourceType.forNumber(bidResponse.getSourceType()).name()));

                // remarketing 的直接放入胜出bids
                if (FloorEntityType.forNumber(bidResponse.getCustomRevShare()).equals(FloorEntityType.REMARKETING_FLOOR)) {
                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, "trumpAd"));
                    trumpBids.add(bidResponse);
                }
                // 归因设备的直接胜出给demond端使用
                if (bidResponse.getAds(0).getAttributionType() == 2) {
                    PerfUtils.perf(PerfConstant.AD_POOL_REQUEST, Tag.of(PerfConstant.AD_POOL_REQUEST_STATUS_TAG, "haveValidResponse"), Tag.of(PerfConstant.AD_POOL, "trumpAttri"));
                    trumpBids.add(bidResponse);
                }
            }
            LogUtils.logInfo(runTimeContext.getRequestId(),"AuctionMarketPlaceResponse trumpBids {}", JSON.toJSONString(trumpBids));
            //没有remarketing胜出 其他广告住胜出 取价格最高的
            Optional<Long> first = validBidResponseList.stream().map(bidResponse -> bidResponse.getAds(0).getAdvBidPrice()).distinct().max(Comparator.comparing(Long::longValue));
            BidResponse winBidResponse = first.map(aLong -> validBidResponseList.stream().filter(bidResponse -> bidResponse.getAds(0).getAdvBidPrice() == aLong).findFirst().get()).orElseGet(() -> validBidResponseList.get(0));
            LogUtils.logInfo(runTimeContext.getRequestId(),"AuctionMarketPlaceResponse placementId {} winBidResponse {}",  runTimeContext.getPlacementId(),JSON.toJSONString(winBidResponse));

            //渠道胜出价格
            FloorEntityType floorEntityType = FloorEntityType.forNumber(winBidResponse.getCustomRevShare());
            runTimeContext.getBidding().put(floorEntityType, winBidResponse.getAds(0).getAdvBidPrice());

            boolean isBaidu = runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get());
            RevenueShareType revenueShareType = RevenueShareType.forNumber(runTimeContext.getPlacementModel().getRevShareType());
            long peDefault = Optional.ofNullable(runTimeContext.getPlacementModel().getPubExpectation()).orElse(0L);
            long pe = peDefault;
            if (null != runTimeContext.getCosmosRevSharePe().get(floorEntityType) && runTimeContext.getCosmosRevSharePe().get(floorEntityType) > 0) {
                pe = runTimeContext.getCosmosRevSharePe().get(floorEntityType);
            }
            runTimeContext.setRealRe(runTimeContext.getCosmosRevSharePe().get(floorEntityType));
            Map<FloorEntityType, BigDecimal> cosmosRevShare = runTimeContext.getCosRevShare();
            // 优先计算出需要胜出的
            if (CollectionUtils.isNotEmpty(trumpBids)) {
                //remarketing trump广告
                BidResponse winBid = trumpBids.get(0);
                Ad winAd = winBid.getAds(0);
                //胜出价格
                long advBidPrice = winAd.getAdvBidPrice();
                //广告主结算价格
                long advSettlePrice = winAd.getAdvSettlePrice();
                // 百度Bes cpc 出价
                long bidPriceCpc = winAd.getAdvBidPriceCpc();
                long advBidPriceOcpm = winAd.getAdvBidPriceOcpm();
                //计算 开发者 广告主 价格
                Map<ChargeAndPay, Long> trumpChargeAndPay = chargeAdvertiserAndPayPublisher(revenueShareType, pe, floorEntityType, cosmosRevShare,
                        runTimeContext, isBaidu, bidPriceCpc, advBidPrice, advSettlePrice,advBidPriceOcpm);

                if (trumpChargeAndPay.get(ChargeAndPay.PAY) <= 0L || trumpChargeAndPay.get(ChargeAndPay.CHARGE) <= 0L) {
                    runTimeContext.getDspMap().put(String.valueOf(winBid.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                    throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
                }
                runTimeContext.setChargeAndPayMap(trumpChargeAndPay);
                runTimeContext.setWinBidResponse(winBid);
                runTimeContext.setWinAd(winAd);
                //markerPlace 不考虑 一价 二价
                runTimeContext.setAdvBidPrice(winAd.getAdvBidPrice());
                runTimeContext.getBidding().put(FloorEntityType.REMARKETING_FLOOR, winAd.getAdvBidPrice());
                runTimeContext.setAdvSettlePrice(trumpChargeAndPay.get(ChargeAndPay.CHARGE));
                runTimeContext.getDspMap().put(String.valueOf(winBid.getSourceType()), DspStaus.AUCTION_WIN_VALUE);
                runTimeContext.setTotalDiscount((float) winAd.getNetDeduction());
                runTimeContext.setAdpoolDeduction(winAd.getAdpoolDeduction());
                return Mono.just(new AuctionDataType());
            }

            //非REMARKETING胜出逻辑处理
            Ad winAd = winBidResponse.getAds(0);
            //set winBidResponse
            runTimeContext.setWinBidResponse(winBidResponse);
            //set winAd
            runTimeContext.setWinAd(winAd);
            //胜出价格
            long advBidPrice = winAd.getAdvBidPrice();
            //广告主结算价格
            long advSettlePrice = winAd.getAdvSettlePrice();
            // 百度Bes cpc 出价
            long bidPriceCpc = winAd.getAdvBidPriceCpc();
            // 百度Bes oCpm 出价
            long advBidPriceOcpm = winAd.getAdvBidPriceOcpm();

            runTimeContext.setAdvBidPrice(advBidPrice);
            Map<ChargeAndPay, Long> chargeAndPayLongMap = chargeAdvertiserAndPayPublisher(revenueShareType, pe, floorEntityType, cosmosRevShare,
                    runTimeContext, isBaidu, bidPriceCpc, advBidPrice, advSettlePrice,advBidPriceOcpm);
            runTimeContext.setChargeAndPayMap(chargeAndPayLongMap);
            runTimeContext.setAdvSettlePrice(chargeAndPayLongMap.get(ChargeAndPay.CHARGE));

            runTimeContext.setTotalDiscount((float) winAd.getNetDeduction());
            runTimeContext.setAdpoolDeduction(winAd.getAdpoolDeduction());

            if (chargeAndPayLongMap.get(ChargeAndPay.PAY) <= 0L || chargeAndPayLongMap.get(ChargeAndPay.CHARGE) <= 0L) {
                runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
            }
            if (winBidResponse.getAdsList().get(0).getBidPriceCpc() > 0L && chargeAndPayLongMap.get(ChargeAndPay.PAY) >= DynamicIntegerConfig.CPC_PRICE_UP.get().longValue() && DynamicStringConfig.BAIDU_ACCOUNT.get().equals(runTimeContext.getPlacementModel().getAccountId())) {
                runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.PAY_OR_CHARGE_ERROR_VALUE);
                log.info("requestId {} price too high", runTimeContext.getRequestId());
                throw new SunException(TermCode.PE_TOO_LOW, runTimeContext);
            }
            runTimeContext.getDspMap().put(String.valueOf(winBidResponse.getSourceType()), DspStaus.AUCTION_WIN_VALUE);
            LogUtils.logInfo(runTimeContext.getRequestId(),"AuctionMarketPlaceResponseEnd placementId {},runTimeContext isError {}", runTimeContext.getPlacementId(),runTimeContext.isError());
            return Mono.just(new AuctionDataType());
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(),"AuctionMarketPlaceResponseError {}",e);
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    /**
     * 计算开发者和广告主对应价格
     * @param revenueShareType 分成模式
     * @param pe
     * @param floorEntityType 渠道
     * @param cosmosRevShare 分成比
     * @param runTimeContext
     * @param isBaidu
     * @param bidPriceCpc cpc 出价
     * @param advBidPrice cpm 出价
     * @param advSettlePrice  广告主价格
     * @return
     */
    private Map<ChargeAndPay, Long> chargeAdvertiserAndPayPublisher(RevenueShareType revenueShareType, long pe, FloorEntityType floorEntityType, Map<FloorEntityType, BigDecimal> cosmosRevShare, RunTimeContext runTimeContext, boolean isBaidu, long bidPriceCpc, long advBidPrice, long advSettlePrice,long advBidPriceOcpm) {
        Map<ChargeAndPay, Long> trumpChargeAndPay;
        if (isBaidu && bidPriceCpc > 0) {
            //百度BES 按照 cpc 出价
            trumpChargeAndPay = calChargeAndPayCpc(revenueShareType, pe, bidPriceCpc, advSettlePrice, cosmosRevShare.get(floorEntityType));
        } else if (isBaidu && advBidPriceOcpm > 0){
            //百度BES 按照 ocpm 出价
            trumpChargeAndPay = calChargeAndPayOcpm(revenueShareType, pe, advBidPriceOcpm, advSettlePrice, cosmosRevShare.get(floorEntityType));
        } else {
            // cpm 出价
            trumpChargeAndPay = calChargeAndPay(revenueShareType, pe, advBidPrice, advSettlePrice, cosmosRevShare.get(floorEntityType));
        }
        Pair<Long, String> newPayAndDealId = dynamicPay(runTimeContext, floorEntityType, null);
        Long newPe = newPayAndDealId.getLeft();
        String newPeDealId = newPayAndDealId.getRight();
        if (newPe != null) {
            //原来的pe计算出来的值还是需要保
            trumpChargeAndPay.put(ChargeAndPay.ORIGIN_PAY, trumpChargeAndPay.get(ChargeAndPay.PAY));
            //替换pay
            trumpChargeAndPay.put(ChargeAndPay.PAY, newPe);
            if (StringUtils.isNotBlank(newPeDealId)) {
                runTimeContext.setNewPeDealId(newPeDealId);
            }
        }
        return trumpChargeAndPay;
    }

    /**
     * @param revenueShareType 分成模式
     * @param pe
     * @param advBidPrice      dsp 返回竞价
     * @param advSettlePrice   广告主价格
     * @param revShare         分成比
     * @return
     */
    private Map<ChargeAndPay, Long> calChargeAndPay(RevenueShareType revenueShareType, Long pe, long advBidPrice, long advSettlePrice, BigDecimal revShare) {
        Long pay;
        Map<ChargeAndPay, Long> map = new HashMap<>();
        //PD 模式分成 计算pay
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
            map.put(ChargeAndPay.CHARGE, advSettlePrice);
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        // fix_rev_share模式 计算pay
        pay = (new BigDecimal(advBidPrice).multiply(revShare)).longValue();

        //pay 最大为 1.1 * pe
//        long maxPe = new BigDecimal(pe).multiply(ONE_POINT_ONE).longValue();
//        if (pay > maxPe) {
//            pay = maxPe;
//        }
        map.put(ChargeAndPay.CHARGE, advSettlePrice);
        map.put(ChargeAndPay.PAY, pay);
        return map;
    }

    /**
     * @param revenueShareType 分成模式
     * @param pe
     * @param cpcPrice         dsp 按照 cpc 出价
     * @param advSettlePrice   广告主价格
     * @param revShare         分成比
     * @return
     */
    private Map<ChargeAndPay, Long> calChargeAndPayCpc(RevenueShareType revenueShareType, Long pe, long cpcPrice, long advSettlePrice, BigDecimal revShare) {
        Long pay;
        Map<ChargeAndPay, Long> map = new HashMap<>();
        //PD 模式分成 计算pay
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
            map.put(ChargeAndPay.CHARGE, advSettlePrice);
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        // fix_rev_share模式 计算pay
        pay = (new BigDecimal(cpcPrice).multiply(revShare)).longValue();

        //pay 最大为 1.1 * pe
//        long maxPe = new BigDecimal(pe).multiply(ONE_POINT_ONE).longValue();
//        if (pay > maxPe) {
//            pay = maxPe;
//        }
        map.put(ChargeAndPay.CHARGE, advSettlePrice);
        map.put(ChargeAndPay.PAY, pay);
        return map;
    }

    /**
     * @param revenueShareType 分成模式
     * @param pe
     * @param advBidPriceOcpm         dsp 按照 ocpm 出价
     * @param advSettlePrice   广告主价格
     * @param revShare         分成比
     * @return
     */
    private Map<ChargeAndPay, Long> calChargeAndPayOcpm(RevenueShareType revenueShareType, Long pe, long advBidPriceOcpm, long advSettlePrice, BigDecimal revShare) {
        Long pay;
        Map<ChargeAndPay, Long> map = new HashMap<>();
        //PD 模式分成 计算pay
        if (revenueShareType.equals(RevenueShareType.PD_REVENUE_SHARE)) {
            pay = pe;
            map.put(ChargeAndPay.CHARGE, advSettlePrice);
            map.put(ChargeAndPay.PAY, pay);
            return map;
        }

        // fix_rev_share模式 计算pay
        pay = (new BigDecimal(advBidPriceOcpm).multiply(revShare)).longValue();

        //pay 最大为 1.1 * pe
//        long maxPe = new BigDecimal(pe).multiply(ONE_POINT_ONE).longValue();
//        if (pay > maxPe) {
//            pay = maxPe;
//        }
        map.put(ChargeAndPay.CHARGE, advSettlePrice);
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
        return Pair.of(newPay, newPayDealId);
    }
}
