package com.inmobi.supply.sun.service.postProc.auction.module;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.api.BidResponse;
import com.inmobi.supply.component.model.dynamicFloor.AvgMetrics;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.constant.SaturnDsp;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.ActiveDeviceIdsRepo;
import com.inmobi.supply.sun.repo.file.PlacementMetricsRepo;
import com.inmobi.supply.sun.repo.redis.impl.DynamicFloorRedisImpl;
import com.inmobi.supply.sun.repo.redis.service.DynamicFloorRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.AuctionParam.jdBidPriceLocation;

/**
 * 动态底价模块（keep on!x）
 * M0在京东feed流上做实验。原因：feed流广告是京东的商品集合页广告，完全根据用户本身在京东的浏览行为产生的广告行为，京东方可以基本可以保证这类广告的点击率
 *
 * @author remi.li
 */
@Service
@Slf4j
public class DynamicFloor implements ModuleWorker<AuctionDataType> {
    private static final String DYNAMIC_FLOOR_ORIGIN_CHARGE_TAG = "dynamicOriginCharge";
    private static final String DYNAMIC_FLOOR_ORIGIN_PAY_TAG = "dynamicOriginPay";

    private static final String DYNAMIC_FLOOR_RAISED_CHARGE_TAG = "dynamicRaisedCharge";
    private static final String DYNAMIC_FLOOR_RAISED_PAY_TAG = "dynamicRaisedPay";

    private static final String DYNAMIC_FLOOR_DECLINED_CHARGE_TAG = "dynamicDeclinedChargeFloor";
    private static final String DYNAMIC_FLOOR_DECLINED_PAY_TAG = "dynamicDeclineadPayFloor";

    @Autowired
    private PlacementMetricsRepo placementMetricsRepo;
    @Autowired
    private ActiveDeviceIdsRepo activeDeviceIdsRepo;
    @Autowired
    private DynamicFloorRedisImpl dynamicFloorRedis;
    @Autowired
    private DynamicFloorRepo dynamicFloorRepo;

    @Override
    public Mono<AuctionDataType> processMono(RunTimeContext runTimeContext) {
        return Mono.just(new AuctionDataType());
        /*try {
            long placementId = runTimeContext.getPlacementId();
            *//*if (!DYNAMIC_FLOOR_EXP_PLACEMENTS.get().contains(placementId)) {
                if (haveWinBidResponse(runTimeContext)) {
                    return Mono.just(new AuctionDataType());
                } else {
                    throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                }
            }*//*
            if (runTimeContext.getAdType() != AdType.FEED) {
                //只在feed流上做实验
                if (haveWinBidResponse(runTimeContext)) {
                    return Mono.just(new AuctionDataType());
                } else {
                    throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                }
            }
            *//*if (runTimeContext.getPlacementModel().getRevShareType() != RevenueShareType.FIXED_REVENUE_SHARE_VALUE) {
                //M0:只对fix revShare做实验
                return Mono.just(new AuctionDataType());
            }*//*

            //广告位，分小时/价格区间的平均表现
            Map<String, Map<String, Double>> placementHourPriceAvgMetricsMap = placementMetricsRepo.getPlacementHourPriceAvgMetricsMap();
            //广告位，分价格区间的平均表现
            Map<String, Map<String, Double>> placementPriceAvgMetricsMap = placementMetricsRepo.getPlacementPriceAvgMetricsMap();
            //分小时/价格区间所有广告位的平均表现
            Map<String, Map<String, Double>> hourPriceAvgMetricsMap = placementMetricsRepo.getHourPriceAvgMetricsMap();
            //真实历史表现
            Map<String, DynamicFloorRepo.ExpClickCharge> expClickChargeMap = dynamicFloorRepo.getPriceLocationExpClickCache();

            if (MapUtils.isEmpty(placementPriceAvgMetricsMap)
                    || MapUtils.isEmpty(placementHourPriceAvgMetricsMap)
                    || MapUtils.isEmpty(hourPriceAvgMetricsMap)
                    || MapUtils.isEmpty(expClickChargeMap)) {
                if (haveWinBidResponse(runTimeContext)) {
                    return Mono.just(new AuctionDataType());
                } else {
                    throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                }
            }

            //获胜的bidResponse
            BidResponse winBidResponse = runTimeContext.getWinBidResponse();
            Ad winAd = runTimeContext.getWinAd();

            int hourOfCurrent = WorkerUtils.hourOfCurrent();
            int hourLocation = WorkerUtils.hourLocation(hourOfCurrent);

            if (winBidResponse != null
                    && !winBidResponse.equals(BidResponse.getDefaultInstance())
                    && !runTimeContext.getChargeAndPayMap().containsKey(ChargeAndPay.ORIGIN_PAY)
                    && winBidResponse.getSourceType() == SourceType.SATURN_VALUE
                    && winAd.getSaturnChannelId().equals(SaturnDsp.JD_CHANNEL_ID)) {
                log.info("jd feed result={} placementId={}", winAd, runTimeContext.getPlacementId());
                long winBidPrice = winAd.getBidPrice(); //京东获胜的bid
                Integer priceLocation = priceLocation(winBidPrice);
                if (priceLocation == null) {
                    //无法定位出价区间（不应该出现这种情况）
                    return Mono.just(new AuctionDataType());
                }
                //实验流量开关：放x%的流量当做对比实验，其余的还是走原来的出价模式
                runTimeContext.getExperimentFlagsMap().put("isDynamicTraffic", false);
                Random random = new Random();
                if (random.nextInt(100) > DYNAMIC_FLOOR_TRAFFIC_PROPORTION.get()) {
                    if (haveWinBidResponse(runTimeContext)) {
                        return Mono.just(new AuctionDataType());
                    } else {
                        throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                    }
                }
                runTimeContext.getExperimentFlagsMap().put("isDynamicTraffic", true);
                Metrics metrics = getMetrics(placementId, hourLocation, priceLocation);
                log.info("metrics={}", metrics);
                double predictPlacementHourPriceAvgCtr = metrics.getPredictPlacementHourPriceAvgCtr(); //ctr
                double predictPlacementHourPriceAdvAvgCpcFen = metrics.getPredictPlacementHourPriceAdvAvgCpcFen(); //广告主cpc
                double predictPlacementHourPriceAdvAvgCpmFen = metrics.getPredictPlacementHourPriceAdvAvgCpmFen(); //广告主cpm
                double predictPlacementHourPricePubAvgCpmFen = metrics.getPredictPlacementHourPricePubAvgCpmFen(); //开发者cpm

                //不区分时间段，广告位在这个出价区间的平均表现情况
                double placementPriceAvgCtr = metrics.getPlacementPriceAvgCtr();
                double placementPriceAdvAvgCpcFen = metrics.getPlacementPriceAdvAvgCpcFen();
                double placementPriceAdvAvgCpmFen = metrics.getPlacementPriceAdvAvgCpmFen();
                double placementPricePubAvgCpmFen = metrics.getPlacementPricePubAvgCpmFen();

                //区分时间段，这个出价区间的平均表现情况（不区分广告位）
                double hourPriceAvgCtr = metrics.getHourPriceAvgCtr();
                double hourPriceAdvAvgCpcFen = metrics.getHourPriceAdvAvgCpcFen();
                double hourPriceAdvAvgCpmFen = metrics.getHourPriceAdvAvgCpmFen();
                double hourPricePubAvgCpmFen = metrics.getHourPricePubAvgCpmFen();

                //前几个小时已经投放的"打标"广告的表现情况
                long historyRaisedExpCount = metrics.getHistoryRaisedExpCount(); //抬高价格的广告的曝光次数
                double historyRaisedClickCount = metrics.getHistoryRaisedClickCount(); //抬高价格的广告的点击次数
                double historyRaisedCharge = metrics.getHistoryRaisedCharge(); //抬高价格的广告收费累加值
                double historyRaisedPay = metrics.getHistoryRaisedPay(); //抬高价格的开发者收费累加值

                long declinedExpCount = metrics.getHistoryDeclinedExpCount(); //降低价格的广告的曝光次数
                double declinedClickCount = metrics.getHistoryDeclinedClickCount(); //降低价格的广告的点击次数
                double declinedCharge = metrics.getHistoryDeclinedCharge(); //降低价格的广告收费累加值
                double declinedPay = metrics.getHistoryDeclinedPay(); //降低价格的开发者收费累加值

                *//*if (historyRaisedClickCount + historyDeclinedClickCount == 0
                        || historyRaisedExpCount + historyDeclinedExpCount == 0) {
                    //打标广告没有历史投放记录(无曝光或者无点击)，直接返回
                    return Mono.just(new AuctionDataType());
                }*//*

                //前N个小时已经投放的"打标"广告的广告主cpc（广告主在意）
                BigDecimal tagedAdvCpc = (BigDecimal.valueOf(historyRaisedClickCount).add(BigDecimal.valueOf(declinedClickCount)).compareTo(BigDecimal.valueOf(0)) == 0)
                        ? BigDecimal.valueOf(0)
                        : (BigDecimal.valueOf(historyRaisedCharge).add(BigDecimal.valueOf(declinedCharge))).multiply(BigDecimal.valueOf(1000))
                        .divide(BigDecimal.valueOf(historyRaisedClickCount).add(BigDecimal.valueOf(declinedClickCount)), 2, RoundingMode.HALF_UP);
                //前N个小时已经投放的"打标"广告的开发者cpm（开发者在意）
                BigDecimal tagedPubCpm = (BigDecimal.valueOf(historyRaisedExpCount).add(BigDecimal.valueOf(declinedExpCount)).compareTo(BigDecimal.valueOf(0)) == 0)
                        ? BigDecimal.valueOf(0)
                        : (BigDecimal.valueOf(historyRaisedPay).add(BigDecimal.valueOf(declinedPay))).multiply(BigDecimal.valueOf(1000))
                        .divide(BigDecimal.valueOf(historyRaisedExpCount).add(BigDecimal.valueOf(declinedExpCount)), 2, RoundingMode.HALF_UP);

                //M0流量选取原则：历史数据统计：这个广告位在这个时间段出价段 比 1. 这个广告位在全时间段这个出价段 2. 这个时间段这个出价段所有广告位
                //ctr高，cpc低，cpm低
                //对广告主来说，效果好（cpc低，ctr高），并且收费收的低的流量
                //对开发者来说，ecpm高
                //上一个小时动态调价的平均ecpm比历史ecpm低

                if (predictPlacementHourPriceAvgCtr > placementPriceAvgCtr && predictPlacementHourPriceAvgCtr > hourPriceAvgCtr
                        && predictPlacementHourPriceAdvAvgCpcFen < placementPriceAdvAvgCpcFen && predictPlacementHourPriceAdvAvgCpcFen < hourPriceAdvAvgCpcFen
                        && predictPlacementHourPriceAdvAvgCpmFen < placementPriceAdvAvgCpmFen && predictPlacementHourPriceAdvAvgCpmFen < hourPriceAdvAvgCpmFen
                        *//*&& predictPlacementHourPricePubAvgCpmFen > placementPricePubAvgCpmFen && predictPlacementHourPricePubAvgCpmFen > hourPricePubAvgCpmFen*//*) {
                    //只对历史表现好的流量进行动态底价操作
                    if (*//*tagedAdvCpc.compareTo(BigDecimal.valueOf(0)) == 0
                            || *//*(tagedAdvCpc.compareTo(BigDecimal.valueOf(predictPlacementHourPriceAdvAvgCpcFen).multiply(BigDecimal.valueOf(DYNAMIC_FLOOR_COEFFICIENT.get()))) < 0
                            && tagedPubCpm.compareTo(BigDecimal.valueOf(predictPlacementHourPriceAdvAvgCpmFen).multiply(BigDecimal.valueOf(DYNAMIC_FLOOR_COEFFICIENT.get()))) < 0)) {
                        //1.对于广告主，在这个广告位上，前几个小时的打标广告的cpc已经低于预估cpc,开发者的cpm已经杜宇预估cpm
                        //2.对于开发者，前几个小时的cpm
                        //可以抬高价格投放
                        double adpoolDeduction = winAd.getAdpoolDeduction(); //4项折扣
                        double netDeduction = winAd.getNetDeduction(); //12项
                        BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
                        BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
                        //M0选取原则:用户相对于该广告位平均值，点击率高，并且成本低，cpm也低
                        //目的：将之前卖的便宜，点击率又好的流量，抬高收费上涨成本
                        Map<ChargeAndPay, Long> chargeAndPayMap = runTimeContext.getChargeAndPayMap();
                        long originCharge = chargeAndPayMap.get(ChargeAndPay.CHARGE);
                        long originPay = chargeAndPayMap.get(ChargeAndPay.PAY);
                        //如果originCharge = winBidPrice - 1，不用抬高价格了
                        if (originCharge <= winBidPrice - FEN * 2) {
                            //抬高价格
                            //抬高originCharge,价差的x%
                            //( winBidPrice - 2 - originCharge) * x%
                            BigDecimal raisedCharge = (BigDecimal.valueOf(winBidPrice).subtract(BigDecimal.valueOf(2)).subtract(BigDecimal.valueOf(originCharge)))
                                    .multiply(BigDecimal.valueOf(DYNAMIC_FLOOR_RAISE_PRICE_PROPORTION.get())
                                            .divide(BigDecimal.valueOf(100), RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP));

                            BigDecimal newRaisedCharge = raisedCharge.add(BigDecimal.valueOf(originCharge));
                            BigDecimal originChargeWithoutCost = BigDecimal.valueOf(originCharge).multiply(twelveDeduction);
                            BigDecimal newRaisedChargeWithoutCost = newRaisedCharge.multiply(twelveDeduction);
                            //扣减掉12项的gap，M0 全部贴补给开发者
                            BigDecimal chargeWithoutCostGap = newRaisedChargeWithoutCost.subtract(originChargeWithoutCost);
                            long newRaisePay = BigDecimal.valueOf(originPay).add(chargeWithoutCostGap).longValue();
                            log.debug("raised price, newRaisedCharge={} originCharge={} newRaisedPay={} originPay={} "
                                            + "predictPlacementHourPriceAdvAvgCpcFen={} predictPlacementHourPriceAdvAvgCpmFen={} "
                                            + "tagedAdvCpc={} tagedPubCpm={} placementId={}",
                                    newRaisedCharge, originCharge, newRaisePay, originPay, predictPlacementHourPriceAdvAvgCpcFen,
                                    predictPlacementHourPriceAdvAvgCpmFen, tagedAdvCpc, tagedPubCpm, placementId);

                            chargeAndPayMap.put(ChargeAndPay.CHARGE, newRaisedCharge.longValue());
                            chargeAndPayMap.put(ChargeAndPay.PAY, newRaisePay);
                            runTimeContext.setChargeAndPayMap(chargeAndPayMap);
                            runTimeContext.setAdvSettlePrice(newRaisedCharge.longValue());

                            //实验流量打标签
                            Map<String, Object> extMap = new HashMap<>();
                            extMap.put(DYNAMIC_FLOOR_ORIGIN_CHARGE_TAG, originCharge);
                            extMap.put(DYNAMIC_FLOOR_ORIGIN_PAY_TAG, originPay);
                            extMap.put(DYNAMIC_FLOOR_RAISED_CHARGE_TAG, newRaisedCharge.longValue());
                            extMap.put(DYNAMIC_FLOOR_RAISED_PAY_TAG, newRaisePay);
                            //runTimeContext.getExperimentFlags().add(ObjectMapperUtils.toJSON(extMap));
                            runTimeContext.getExperimentFlagsMap().putAll(extMap);
                            return Mono.just(new AuctionDataType());
                        }
                    }
                }
            }

            if (winBidResponse == null) {
                List<BidResponse> lowerThanFloorJdFeedResponse = runTimeContext.getLowerThanFloorBidResponseList()
                        .stream().filter(bidResponse -> bidResponse.getSourceType() == SourceType.SATURN_VALUE
                                && bidResponse.getAdsList().get(0).getSaturnChannelId().equals(SaturnDsp.JD_CHANNEL_ID)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(lowerThanFloorJdFeedResponse)) {
                    BidResponse jdBidResponse = lowerThanFloorJdFeedResponse.get(0);
                    Ad jdAd = lowerThanFloorJdFeedResponse.get(0).getAdsList().get(0);
                    //京东返回结果，并且结果小于底价
                    long bidPrice = jdAd.getBidPrice();
                    Integer priceLocation = priceLocation(bidPrice);
                    if (priceLocation == null) {
                        //无法定位出价区间（不应该出现这种情况）
                        return Mono.just(new AuctionDataType());
                    }

                    runTimeContext.getExperimentFlagsMap().put("isDynamicTraffic", false);
                    Random random = new Random();
                    if (random.nextInt(100) > DYNAMIC_FLOOR_TRAFFIC_PROPORTION.get()) {
                        if (haveWinBidResponse(runTimeContext)) {
                            return Mono.just(new AuctionDataType());
                        } else {
                            throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                        }
                    }
                    runTimeContext.getExperimentFlagsMap().put("isDynamicTraffic", true);

                    Metrics metrics = getMetrics(placementId, hourLocation, priceLocation);

                    double predictPlacementHourPriceAvgCtr = metrics.getPredictPlacementHourPriceAvgCtr(); //ctr
                    double predictPlacementHourPriceAdvAvgCpcFen = metrics.getPredictPlacementHourPriceAdvAvgCpcFen(); //广告主cpc
                    double predictPlacementHourPriceAdvAvgCpmFen = metrics.getPredictPlacementHourPriceAdvAvgCpmFen(); //广告主cpm
                    double predictPlacementHourPricePubAvgCpmFen = metrics.getPredictPlacementHourPricePubAvgCpmFen(); //开发者cpm

                    //不区分时间段，广告位在这个出价区间的平均表现情况
                    double placementPriceAvgCtr = metrics.getPlacementPriceAvgCtr();
                    double placementPriceAdvAvgCpcFen = metrics.getPlacementPriceAdvAvgCpcFen();
                    double placementPriceAdvAvgCpmFen = metrics.getPlacementPriceAdvAvgCpmFen();
                    double placementPricePubAvgCpmFen = metrics.getPlacementPricePubAvgCpmFen();

                    //区分时间段，这个出价区间的平均表现情况（不区分广告位）
                    double hourPriceAvgCtr = metrics.getHourPriceAvgCtr();
                    double hourPriceAdvAvgCpcFen = metrics.getHourPriceAdvAvgCpcFen();
                    double hourPriceAdvAvgCpmFen = metrics.getHourPriceAdvAvgCpmFen();
                    double hourPricePubAvgCpmFen = metrics.getHourPricePubAvgCpmFen();

                    //前几个小时已经投放的"打标"广告的表现情况
                    long historyRaisedExpCount = metrics.getHistoryRaisedExpCount(); //抬高价格的广告的曝光次数
                    double historyRaisedClickCount = metrics.getHistoryRaisedClickCount(); //抬高价格的广告的点击次数
                    double historyRaisedCharge = metrics.getHistoryRaisedCharge(); //抬高价格的广告收费累加值
                    double historyRaisedPay = metrics.getHistoryRaisedPay(); //抬高价格的开发者收费累加值

                    long declinedExpCount = metrics.getHistoryDeclinedExpCount(); //降低价格的广告的曝光次数
                    double declinedClickCount = metrics.getHistoryDeclinedClickCount(); //降低价格的广告的点击次数
                    double declinedCharge = metrics.getHistoryDeclinedCharge(); //降低价格的广告收费累加值
                    double declinedPay = metrics.getHistoryDeclinedPay(); //降低价格的开发者收费累加值

                    *//*if (historyRaisedClickCount + historyDeclinedClickCount == 0
                            || historyRaisedExpCount + historyDeclinedExpCount == 0) {
                        //打标广告没有历史投放记录(无曝光或者无点击)，直接返回
                        return Mono.just(new AuctionDataType());
                    }*//*

                    //前N个小时已经投放的"打标"广告的广告主cpc（广告主在意）
                    BigDecimal tagedAdvCpc = (BigDecimal.valueOf(historyRaisedClickCount).add(BigDecimal.valueOf(declinedClickCount)).compareTo(BigDecimal.valueOf(0)) == 0)
                            ? BigDecimal.valueOf(0)
                            : (BigDecimal.valueOf(historyRaisedCharge).add(BigDecimal.valueOf(declinedCharge))).multiply(BigDecimal.valueOf(1000))
                            .divide(BigDecimal.valueOf(historyRaisedClickCount).add(BigDecimal.valueOf(declinedClickCount)), 2, RoundingMode.HALF_UP);
                    //前N个小时已经投放的"打标"广告的开发者cpm（开发者在意）
                    BigDecimal tagedPubCpm = (BigDecimal.valueOf(historyRaisedExpCount).add(BigDecimal.valueOf(declinedExpCount)).compareTo(BigDecimal.valueOf(0)) == 0)
                            ? BigDecimal.valueOf(0)
                            : (BigDecimal.valueOf(historyRaisedPay).add(BigDecimal.valueOf(declinedPay))).multiply(BigDecimal.valueOf(1000))
                            .divide(BigDecimal.valueOf(historyRaisedExpCount).add(BigDecimal.valueOf(declinedExpCount)), 2, RoundingMode.HALF_UP);
                    String key = SaturnDsp.JD_CHANNEL_ID + AdType.FEED_VALUE;
                    List<String> activeIdfas = activeDeviceIdsRepo.getActiveIdfaMap().getOrDefault(key, new ArrayList<>());
                    List<String> activeImeiMd5s = activeDeviceIdsRepo.getActiveImeiMd5Map().getOrDefault(key, new ArrayList<>());
                    List<String> activeImeis = activeDeviceIdsRepo.getActiveImeiMap().getOrDefault(key, new ArrayList<>());
                    List<String> activeAndroidMd5Ids = activeDeviceIdsRepo.getActiveAndroidIdMd5Map().getOrDefault(key, new ArrayList<>());
                    List<String> activeAndroidSha1Ids = activeDeviceIdsRepo.getActiveAndroidIdSha1Map().getOrDefault(key, new ArrayList<>());
                    List<String> activeOaidIds = activeDeviceIdsRepo.getActiveOaidMap().getOrDefault(key, new ArrayList<>());
                    Device.IdMapping deviceId = runTimeContext.getDevice().getId();
                    //判断是否是dsp的最近一段时间的活跃用户
                    boolean isJdActiveUser = activeIdfas.contains(deviceId.getIdfa())
                            || activeImeiMd5s.contains(deviceId.getImeiMd5())
                            || activeImeis.contains(deviceId.getImei())
                            || activeAndroidMd5Ids.contains(deviceId.getUm5())
                            || activeAndroidSha1Ids.contains(deviceId.getO1())
                            || activeOaidIds.contains(deviceId.getOaid());
                    //对预估表现差的流量进行动态底价操作
                    if (predictPlacementHourPriceAvgCtr < placementPriceAvgCtr && predictPlacementHourPriceAvgCtr < hourPriceAvgCtr
                            || predictPlacementHourPriceAdvAvgCpcFen > placementPriceAdvAvgCpcFen && predictPlacementHourPriceAdvAvgCpcFen > hourPriceAdvAvgCpcFen
                            || predictPlacementHourPriceAdvAvgCpmFen > placementPriceAdvAvgCpmFen && predictPlacementHourPriceAdvAvgCpmFen > hourPriceAdvAvgCpmFen
                            *//*&& predictPlacementHourPricePubAvgCpmFen > placementPricePubAvgCpmFen && predictPlacementHourPricePubAvgCpmFen > hourPricePubAvgCpmFen*//*) {
                        log.info("declined metrics={} ad={} placementId={} tagedAdvCpc={} tagedPubCpm={}", metrics, jdAd, placementId, tagedAdvCpc, tagedPubCpm);
                        if (tagedAdvCpc.compareTo(BigDecimal.valueOf(predictPlacementHourPriceAdvAvgCpcFen).multiply(BigDecimal.valueOf(DYNAMIC_FLOOR_COEFFICIENT.get()))) > 0
                                || tagedPubCpm.compareTo(BigDecimal.valueOf(predictPlacementHourPricePubAvgCpmFen).multiply(BigDecimal.valueOf(DYNAMIC_FLOOR_COEFFICIENT.get()))) > 0) {
                            //1.对于广告主，在这个广告位上，前几个小时的打标广告的cpc已经高于预估cpc
                            //2.对于开发者，前几个小时的cpm高于预估pubEcpm
                            //可以进行降价投放（投放低于底价的广告）
                            //先去掉这个条件限制
                            double adpoolDeduction = jdAd.getAdpoolDeduction(); //4项折扣
                            double netDeduction = jdAd.getNetDeduction(); //12项
                            BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(adpoolDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 四项折扣 1 - adpoolDeduction/1.06
                            BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(netDeduction).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06

                            //M0:选二价=bid-1,好处是最大程度上保证了开发者的利益，不好的地方在于，这个charge大于predictCpmFen，可能会造成广告主的成本高
                            BigDecimal highestSecondPrice = BigDecimal.valueOf(bidPrice - FEN); //最高的出价即为出价减去一分钱
                            BigDecimal highestRawCharge = highestSecondPrice
                                    .divide(fourDeduction, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
                            BigDecimal highestRawChargeWithoutCost = highestRawCharge.multiply(twelveDeduction);
                            BigDecimal highestRawChargeWithoutCost90 = highestRawChargeWithoutCost.multiply(BigDecimal.valueOf(0.90)); //margin一定保留10%
                            BigDecimal peBig = BigDecimal.valueOf(runTimeContext.getPlacementModel().getPubExpectation()); //pe
                            BigDecimal peBig95 = peBig.multiply(BigDecimal.valueOf(0.95));

                            long highestPay;
                            if (highestRawChargeWithoutCost90.compareTo(peBig) < 0) {
                                if (highestRawChargeWithoutCost90.compareTo(peBig95) >= 0) {
                                    highestPay = peBig95.longValue();
                                } else {
                                    BigDecimal proportion = BigDecimal.valueOf(DYNAMIC_FLOOR_DECLINED_PRICE_PROPORTION.get())
                                            .divide(BigDecimal.valueOf(100), RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
                                    if (peBig.subtract(highestRawChargeWithoutCost90).compareTo(peBig.multiply(proportion)) <= 0) {
                                        //少给的部分占了pe x% 以下,本次给予投放
                                        highestPay = highestRawChargeWithoutCost90.longValue();
                                    } else {
                                        log.debug("declined enter here, highestRawChargeWithoutCost90={} peBig={} tagedAdvCpc={} tagedPubCpm={}",
                                                highestRawChargeWithoutCost90, peBig, tagedAdvCpc, tagedPubCpm);
                                        throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
                                    }
                                }
                            } else {
                                highestPay = peBig.longValue();
                            }
                            //比例动态可以配置，越大，开发者亏得越多
                            log.debug("decline price, highestSecondPrice={} highestRawCharge={} highestRawChargeWithoutCost={} "
                                            + "highestPay={} pe={} tagedAdvCpc={} tagedPubCpm={}",
                                    highestSecondPrice, highestRawCharge, highestRawChargeWithoutCost, highestPay, peBig, tagedAdvCpc, tagedPubCpm);
                            long newFloor = highestSecondPrice.subtract(BigDecimal.valueOf(FEN)).longValue();
                            Map<ChargeAndPay, Long> chargeAndPayLongMap = new HashMap<>();
                            chargeAndPayLongMap.put(ChargeAndPay.CHARGE, highestRawCharge.longValue());
                            chargeAndPayLongMap.put(ChargeAndPay.PAY, highestPay);

                            FloorEntityType floorEntityType = FloorEntityType.forNumber(jdBidResponse.getCustomRevShare());

                            //暂时不重置渠道底价
                            runTimeContext.getFloorEntityMap().put(floorEntityType, newFloor);
                            runTimeContext.setChargeAndPayMap(chargeAndPayLongMap);
                            runTimeContext.setWinBidResponse(jdBidResponse);
                            runTimeContext.setWinAd(jdAd);
                            runTimeContext.setFirstPrice(bidPrice);
                            runTimeContext.setSecondPrice(highestSecondPrice.longValue());
                            runTimeContext.setTotalDiscount((float) netDeduction);
                            runTimeContext.setAdpoolDeduction(adpoolDeduction);
                            runTimeContext.getBidding().put(floorEntityType, jdAd.getBidPrice());
                            runTimeContext.setAdvSettlePrice(chargeAndPayLongMap.get(ChargeAndPay.CHARGE));

                            //实验流量打标签
                            Map<String, Object> extMap = new HashMap<>();
                            extMap.put(DYNAMIC_FLOOR_DECLINED_CHARGE_TAG, highestRawCharge.longValue());
                            extMap.put(DYNAMIC_FLOOR_DECLINED_PAY_TAG, highestPay);
                            //runTimeContext.getExperimentFlags().add(ObjectMapperUtils.toJSON(extMap));
                            runTimeContext.getExperimentFlagsMap().putAll(extMap);
                            //return Mono.just(new AuctionDataType());
                        }
                    }
                }
            }
            if (haveWinBidResponse(runTimeContext)) {
                //最终还是没有竞价成功的广告
                return Mono.just(new AuctionDataType());
            }
            throw new SunException(TermCode.NO_VALID_AD_POOL_RESPONSE, runTimeContext);
        } catch (SunException e) {
            return Mono.error(e);
        } catch (Throwable e) {
            log.info("dynamicFloor error", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }*/
    }

    private boolean haveWinBidResponse(RunTimeContext runTimeContext) {
        return runTimeContext.getWinBidResponse() != null && !runTimeContext.getWinBidResponse().equals(BidResponse.getDefaultInstance());
    }

    private String placementMetricRedisKey(String channelId, long placementId, int adType, int priceLocation, int hour) {
        return RedisKey.PLACEMENT_METRICS.of(channelId, String.valueOf(placementId), String.valueOf(adType), String.valueOf(priceLocation), String.valueOf(hour));
    }

    private String placementMetricRedisKey(String channelId, int adType, int priceLocation, int hour) {
        return RedisKey.PLACEMENT_METRICS.of(channelId, String.valueOf(adType), String.valueOf(priceLocation), String.valueOf(hour));
    }

    private String placementMetricRedisKey(String channelId, long placementId, int adType, int priceLocation) {
        return RedisKey.PLACEMENT_METRICS.of(channelId, String.valueOf(placementId), String.valueOf(adType), String.valueOf(priceLocation));
    }

    private String raisedEcpmPerHourRedisKey(long placementId, int hour) {
        return RedisKey.DYNAMIC_FLOOR_RAISED_ECPM_PER_HOUR.of(String.valueOf(placementId), String.valueOf(hour));
    }

    private String declinedEcpmPerHourRedisKey(long placementId, int hour) {
        return RedisKey.DYNAMIC_FLOOR_DECLINED_ECPM_PER_HOUR.of(String.valueOf(placementId), String.valueOf(hour));
    }

    private Integer priceLocation(long bidPrice) {
        Integer priceLocation = null;
        //决定bid的出价区间
        for (Map.Entry<Integer, Pair<Integer, Integer>> entry : jdBidPriceLocation.entrySet()) {
            int location = entry.getKey();
            Pair<Integer, Integer> pricePair = entry.getValue();
            if (bidPrice >= pricePair.getLeft() && bidPrice <= pricePair.getRight()) {
                //出价落在了某一个出价区间内
                priceLocation = location;
                break;
            }
        }
        return priceLocation;
    }

    /**
     * 过去一个小时内升高ecpm与下降ecpm的均值
     *
     * @return
     */
    private long lastHourDynamicAvgEcpm(long placementId, int lastHour) {
        DynamicFloorRepo.PlacementHour placementHour = new DynamicFloorRepo.PlacementHour(placementId, lastHour);
        long dynamicEcpmNum = Optional.ofNullable(dynamicFloorRepo.getPlaceEcpmNumCache()).orElse(new HashMap<>()).getOrDefault(placementHour, 0L);
        if (dynamicEcpmNum != 0L) {
            return (Optional.ofNullable(dynamicFloorRepo.getPlaceRaisedEcpmCache()).orElse(new HashMap<>()).getOrDefault(placementHour, 0L) +
                    Optional.ofNullable(dynamicFloorRepo.getPlaceDeclinedCache()).orElse(new HashMap<>()).getOrDefault(placementHour, 0L)) / dynamicEcpmNum;
        }
        return 0L;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Metrics {
        //获取历史这个时间段,广告位在这个出价区间dsp的表现情况（当做预估值）
        double predictPlacementHourPriceAvgCtr; //ctr
        double predictPlacementHourPriceAdvAvgCpcFen; //广告主cpc
        double predictPlacementHourPriceAdvAvgCpmFen; //广告主cpm
        double predictPlacementHourPricePubAvgCpmFen; //开发者cpm

        //不区分时间段，广告位在这个出价区间的平均表现情况
        double placementPriceAvgCtr;
        double placementPriceAdvAvgCpcFen;
        double placementPriceAdvAvgCpmFen;
        double placementPricePubAvgCpmFen;

        //区分时间段，这个出价区间的平均表现情况（不区分广告位）
        double hourPriceAvgCtr;
        double hourPriceAdvAvgCpcFen;
        double hourPriceAdvAvgCpmFen;
        double hourPricePubAvgCpmFen;

        //前几个小时已经投放的"打标"广告的表现情况
        long historyRaisedExpCount; //抬高价格的广告的曝光次数
        double historyRaisedClickCount; //抬高价格的广告的点击次数
        double historyRaisedCharge; //抬高价格的广告收费累加值
        double historyRaisedPay; //抬高价格的开发者收费累加值

        long historyDeclinedExpCount; //降低价格的广告的曝光次数
        double historyDeclinedClickCount; //降低价格的广告的点击次数
        double historyDeclinedCharge; //降低价格的广告收费累加值
        double historyDeclinedPay; //降低价格的开发者收费累加值
    }

    private Metrics getMetrics(long placementId, int hourLocation, int priceLocation) {
        //广告位，分小时/价格区间的平均表现
        Map<String, Map<String, Double>> placementHourPriceAvgMetricsMap = placementMetricsRepo.getPlacementHourPriceAvgMetricsMap();
        //广告位，分价格区间的平均表现
        Map<String, Map<String, Double>> placementPriceAvgMetricsMap = placementMetricsRepo.getPlacementPriceAvgMetricsMap();
        //分小时/价格区间所有广告位的平均表现
        Map<String, Map<String, Double>> hourPriceAvgMetricsMap = placementMetricsRepo.getHourPriceAvgMetricsMap();
        //真实历史表现
        Map<String, DynamicFloorRepo.ExpClickCharge> expClickChargeMap = dynamicFloorRepo.getPriceLocationExpClickCache();

//        log.info("expClickChargeMap={}", expClickChargeMap);
        String placementHourPriceAvgMetricsQueryKey = placementMetricRedisKey(SaturnDsp.JD_CHANNEL_ID, placementId, AdType.FEED_VALUE, priceLocation, hourLocation);
        String placementPriceAvgMetricsQueryKey = placementMetricRedisKey(SaturnDsp.JD_CHANNEL_ID, placementId, AdType.FEED_VALUE, priceLocation);
        String hourPriceAvgMetricsQueryKey = placementMetricRedisKey(SaturnDsp.JD_CHANNEL_ID, AdType.FEED_VALUE, priceLocation, hourLocation);
        String expClickQueryKey = placementId + "_" + priceLocation;
        log.debug("placementHourPriceAvgMetricsQueryKey={} placementPriceAvgMetricsQueryKey={} hourPriceAvgMetricsQueryKey={}",
                placementHourPriceAvgMetricsQueryKey, placementPriceAvgMetricsQueryKey, hourPriceAvgMetricsQueryKey);


        log.debug("queryValue={}, {}, {}",
                placementHourPriceAvgMetricsMap.getOrDefault(placementHourPriceAvgMetricsQueryKey, new HashMap<>()),
                placementPriceAvgMetricsMap.getOrDefault(placementPriceAvgMetricsQueryKey, new HashMap<>()),
                hourPriceAvgMetricsMap.getOrDefault(hourPriceAvgMetricsQueryKey, new HashMap<>()));

        Metrics metrics = new Metrics();
        if (placementHourPriceAvgMetricsMap.containsKey(placementHourPriceAvgMetricsQueryKey)
                && placementPriceAvgMetricsMap.containsKey(placementPriceAvgMetricsQueryKey)
                && hourPriceAvgMetricsMap.containsKey(hourPriceAvgMetricsQueryKey)) {
            //获取历史这个时间段,广告位在这个出价区间dsp的表现情况（当做预估值）
            Map<String, Double> placementHourPriceMapValue = placementHourPriceAvgMetricsMap.get(placementHourPriceAvgMetricsQueryKey);
            AvgMetrics placementHourPriceAvgMetrics = ObjectMapperUtils.fromJSON(ObjectMapperUtils.toJSON(placementHourPriceMapValue), AvgMetrics.class);
            double predictPlacementHourPriceAvgCtr = placementHourPriceAvgMetrics.getCtr(); //ctr
            double predictPlacementHourPriceAdvAvgCpcFen = placementHourPriceAvgMetrics.getAdvCpcFen(); //广告主cpc
            double predictPlacementHourPriceAdvAvgCpmFen = placementHourPriceAvgMetrics.getAdvCpmFen(); //广告主cpm
            double predictPlacementHourPricePubAvgCpmFen = placementHourPriceAvgMetrics.getPubCpmFen(); //开发者cpm

            //不区分时间段，广告位在这个出价区间的平均表现情况
            Map<String, Double> placementPriceMapValue = placementPriceAvgMetricsMap.get(placementPriceAvgMetricsQueryKey);
            AvgMetrics placementPriceAvgMetrics = ObjectMapperUtils.fromJSON(ObjectMapperUtils.toJSON(placementPriceMapValue), AvgMetrics.class);
            double placementPriceAvgCtr = placementPriceAvgMetrics.getCtr();
            double placementPriceAdvAvgCpcFen = placementPriceAvgMetrics.getAdvCpcFen();
            double placementPriceAdvAvgCpmFen = placementPriceAvgMetrics.getAdvCpmFen();
            double placementPricePubAvgCpmFen = placementPriceAvgMetrics.getPubCpmFen();

            //区分时间段，这个出价区间的平均表现情况（不区分广告位）
            Map<String, Double> hourPriceMapValue = hourPriceAvgMetricsMap.get(hourPriceAvgMetricsQueryKey);
            AvgMetrics hourPriceAvgMetrics = ObjectMapperUtils.fromJSON(ObjectMapperUtils.toJSON(hourPriceMapValue), AvgMetrics.class);
            double hourPriceAvgCtr = hourPriceAvgMetrics.getCtr();
            double hourPriceAdvAvgCpcFen = hourPriceAvgMetrics.getAdvCpcFen();
            double hourPriceAdvAvgCpmFen = hourPriceAvgMetrics.getAdvCpmFen();
            double hourPricePubAvgCpmFen = hourPriceAvgMetrics.getPubCpmFen();

            metrics.setPredictPlacementHourPriceAvgCtr(predictPlacementHourPriceAvgCtr);
            metrics.setPredictPlacementHourPriceAdvAvgCpcFen(predictPlacementHourPriceAdvAvgCpcFen);
            metrics.setPredictPlacementHourPriceAdvAvgCpmFen(predictPlacementHourPriceAdvAvgCpmFen);
            metrics.setPredictPlacementHourPricePubAvgCpmFen(predictPlacementHourPricePubAvgCpmFen);

            metrics.setPlacementPriceAvgCtr(placementPriceAvgCtr);
            metrics.setPlacementPriceAdvAvgCpcFen(placementPriceAdvAvgCpcFen);
            metrics.setPlacementPriceAdvAvgCpmFen(placementPriceAdvAvgCpmFen);
            metrics.setPlacementPricePubAvgCpmFen(placementPricePubAvgCpmFen);

            metrics.setHourPriceAvgCtr(hourPriceAvgCtr);
            metrics.setHourPriceAdvAvgCpcFen(hourPriceAdvAvgCpcFen);
            metrics.setHourPriceAdvAvgCpmFen(hourPriceAdvAvgCpmFen);
            metrics.setHourPricePubAvgCpmFen(hourPricePubAvgCpmFen);
        }

        if (expClickChargeMap.containsKey(expClickQueryKey)) {
            //前几个小时已经投放的"打标"广告的表现情况
            DynamicFloorRepo.ExpClickCharge expClickCharge = expClickChargeMap.get(expClickQueryKey);
            long historyRaisedExpCount = expClickCharge.getRaisedExpCount(); //抬高价格的广告的曝光次数
            double historyRaisedClickCount = expClickCharge.getRaisedClickCount(); //抬高价格的广告的点击次数
            double historyRaisedCharge = expClickCharge.getRaisedCharge(); //抬高价格的广告收费累加值
            double historyRaisedPay = expClickCharge.getRaisedPay(); //抬高价格的开发者收费累加值

            long declinedExpCount = expClickCharge.getDeclinedExpCount(); //降低价格的广告的曝光次数
            double declinedClickCount = expClickCharge.getDeclinedClickCount(); //降低价格的广告的点击次数
            double declinedCharge = expClickCharge.getDeclinedCharge(); //降低价格的广告收费累加值
            double declinedPay = expClickCharge.getDeclinedPay(); //降低价格的开发者收费累加值
            metrics.setHistoryRaisedExpCount(historyRaisedExpCount);
            metrics.setHistoryRaisedClickCount(historyRaisedClickCount);
            metrics.setHistoryRaisedCharge(historyRaisedCharge);
            metrics.setHistoryRaisedPay(historyRaisedPay);
            metrics.setHistoryDeclinedExpCount(declinedExpCount);
            metrics.setHistoryDeclinedClickCount(declinedClickCount);
            metrics.setHistoryDeclinedCharge(declinedCharge);
            metrics.setHistoryDeclinedPay(declinedPay);
        }
        return metrics;
    }
}