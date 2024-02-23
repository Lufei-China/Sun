package com.inmobi.supply.sun.service.preProc.call;

import cn.inmobi.pb.api.*;
import com.alibaba.fastjson.JSON;
import com.framework.util.date.DateUtils;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.mixFlowRate.H5Request;
import com.inmobi.supply.sun.model.mixFlowRate.MixFlowRateCreative;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.MixFlowRateRepo;
import com.inmobi.supply.sun.repo.redis.service.MixFlowBurnRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import static com.inmobi.supply.sun.config.dynamic.DynamicFloatConfig.MIX_FLOW_BID_TIMES_PE;
import static com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig.*;
import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.MIX_FLOW_RATE_LOW_LIST;
import static com.inmobi.supply.sun.config.dynamic.DynamicStringConfig.MONITOR_EVENT_HOST;
import static com.inmobi.supply.sun.constant.RuntimeConstant.ONE_POINT_ZERO_SIX;

@Service
@Slf4j
public class MixFlowRateService {

    @Autowired
    private MixFlowRateRepo mixFlowRateRepo;

    @Autowired
    RedisScript<String> redisScript;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MixFlowBurnRepo mixFlowBurnRepo;

    private RateLimiter rateLimiter = RateLimiter.create(20);


    @Nullable
    public BidResponse buildBidResponse(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(), "MixFlowRateService");
        try {
            // 时间开关
            String hour = DateUtils.getHour(new Date());
            boolean timeFlag = Integer.parseInt(hour) >= MIX_FLOW_START_HOUR.get() && Integer.parseInt(hour) <= MIX_FLOW_END_HOUR.get();
            if (!timeFlag) {
                return null;
            }
            // 当日burn开关
            boolean burnFlag = mixFlowBurnRepo.getMixFlowTodayBurn() != null && mixFlowBurnRepo.getMixFlowTodayBurn() <= MIX_FLOW_BURN_LIMIT.get();
            if (!burnFlag) {
                return null;
            }
            PlacementModel placementModel = runTimeContext.getPlacementModel();

            Integer fillLimit;
            Integer eventLimit;
            if (MIX_FLOW_RATE_LOW_LIST.get().contains(placementModel.getId())) {
                fillLimit = MIX_FLOW_RATE_LOW_COUNT_LIMIT.get();
                eventLimit = MIX_FLOW_RATE_EVENT_LOW_COUNT_LIMIT.get();
            }
            else {
                fillLimit = MIX_FLOW_RATE_COUNT_LIMIT.get();
                eventLimit = MIX_FLOW_RATE_EVENT_COUNT_LIMIT.get();
            }

            // 当日分广告位探测结果开关
            Integer currentEventCount = mixFlowBurnRepo.getMixFlowTodayEvent().get(placementModel.getId().toString());
            if (currentEventCount != null && currentEventCount >= eventLimit ) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "{}  currentEventCount {} eventLimit {}", placementModel.getId(), currentEventCount, eventLimit);
                return null;
            }

            /**
             * 单机每秒通过20 QPS
             */
            if (!rateLimiter.tryAcquire()) {
                return null;
            }
            PerfUtils.perf("sun.mixFlowRateService.qps");
            //限流控制 单个广告位每小时 10次
            String flowRateKey = "MIX_FLOW_RATE_" + placementModel.getId() + "_" + hour;
            if (Integer.valueOf(0).equals(fillLimit)) {
                return null;
            }
            // 这里调用block回抛异常，简单处理改为阻塞式redisTemplate
            String value = redisTemplate.execute(redisScript, Lists.newArrayList(flowRateKey), fillLimit + "", (60 * 60) + "");
            LogUtils.logInfo(runTimeContext.getRequestId(), "mixFlowRateServiceRedisScript  {}  low model", value);
            if (StringUtils.isBlank(value) || "2".equals(value)) {
                return null;
            }
            BidRequest bidRequest = runTimeContext.getBidRequest();
            List<CreativeTypeRatio> creativeTypeRatioList = bidRequest.getAdslots(0).getAcceptedCreativeTypeRatiosList();
            if (CollectionUtils.isEmpty(creativeTypeRatioList)) {
                return null;
            }
            List<String> allWhRatio = creativeTypeRatioList.stream().map(CreativeTypeRatio::getWhRatio).distinct().collect(Collectors.toList());
            if (CollectionUtils.isEmpty(allWhRatio)) {
                return null;
            }
            String mixFlowRateKey = placementModel.getType() + "_" + allWhRatio.get(0).replace(":", "_");
            MixFlowRateCreative mixFlowRateCreative = mixFlowRateRepo.getMixFlowRateMap().getOrDefault(mixFlowRateKey.toString(), null);
            if (null == mixFlowRateCreative) {
                return null;
            }
            BidResponse.Builder responseBuilder = BidResponse.newBuilder();
            Ad.Builder adBuilder = Ad.newBuilder();
            responseBuilder.setRequestId(runTimeContext.getRequestId());
            responseBuilder.setProcessingTimeMs(80);
            responseBuilder.setSourceType(SourceType.SATURN_VALUE);
            responseBuilder.setSaturnDsp("mixFlowRateRTB");
            responseBuilder.setCustomRevShare(SourceType.SATURN_VALUE);

            // bid = floor/twelveDeduction * fourDeduction / 0.95
            BigDecimal fourDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.06)).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
            BigDecimal twelveDeduction = BigDecimal.valueOf(1).subtract(BigDecimal.valueOf(0.2725).divide(ONE_POINT_ZERO_SIX, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)); // 十二折扣 1 - advTotalDiscount/1.06
            //RS
            BigDecimal bigDecimal = null == runTimeContext.getCosRevShare().get(FloorEntityType.SATURN_FLOOR) ? new BigDecimal("0.95") : runTimeContext.getCosRevShare().get(FloorEntityType.SATURN_FLOOR);
            //PE   百分比
            Long pubExpectation = placementModel.getPubExpectation();

            // 混量返回的价格为 pe*1.2
            Long realPubExpectation = new BigDecimal(pubExpectation).multiply(BigDecimal.valueOf(MIX_FLOW_BID_TIMES_PE.get())).longValue();
            BigDecimal floor = (new BigDecimal(realPubExpectation)).divide(bigDecimal, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);
            BigDecimal advSettlePrice = floor.divide(twelveDeduction, 0, RoundingMode.HALF_UP).multiply(fourDeduction).divide(new BigDecimal("0.95"), RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP);

            LogUtils.logInfo(runTimeContext.getRequestId(), "mixFlowRateInfoPrice fourDeduction {},twelveDeduction {}," +
                    "rs {},pe {}, realPubExpectation {}, floor {}, advSettlePrice {}", fourDeduction, twelveDeduction, bigDecimal, pubExpectation, realPubExpectation, floor, advSettlePrice);

            adBuilder.setAdId(String.valueOf(RandomUtils.nextLong(10000, 20000)));
            //胜出价格为 pe * 1.2
            adBuilder.setAdvBidPrice(floor.longValue());
            //广告主结算价格
            adBuilder.setAdvSettlePrice(advSettlePrice.longValue());
            adBuilder.setBidType(0);
            adBuilder.setSecondBidPrice(pubExpectation);
            adBuilder.setSaturnChannelId("mixFlowRateChannel");
            adBuilder.setSaturnSegmentId("mixFlowRateSegmentID");
            adBuilder.setSettleType(1);
            adBuilder.setNetDeduction(0.2725);
            adBuilder.setAdpoolDeduction(0.06);
            MaterialMeta.Builder creativeBuilder = MaterialMeta.newBuilder();
            creativeBuilder.setCreativeType(CreativeType.IMAGE_VALUE)
                    .setInteractionType(InteractionType.OPEN_IN_APP_VALUE)
                    .setTitle(Optional.ofNullable(mixFlowRateCreative.getTitle()).orElse(""))
                    .setDescription(Optional.ofNullable(mixFlowRateCreative.getDescription()).orElse(""));
            creativeBuilder.setImage(Image.newBuilder()
                    .setUrl(mixFlowRateCreative.getImage().getUrl())
                    .setWidth(mixFlowRateCreative.getImage().getWidth())
                    .setHeight(mixFlowRateCreative.getImage().getHeight()));

            String targetUrl = getTargetUrl(runTimeContext, mixFlowRateCreative.getTargetUrl());
            creativeBuilder.setTargetUrl(targetUrl);
            adBuilder.setCreative(creativeBuilder);
            responseBuilder.addAds(adBuilder);
            runTimeContext.setMixAd(true);
            LogUtils.logInfo(runTimeContext.getRequestId(), "mixFlowResponse {} {}", placementModel.getId(), responseBuilder.build());
            return responseBuilder.build();
        } catch (Exception e) {
            PerfUtils.perf("sun.mixFlowRate.error");
            LogUtils.logError(runTimeContext.getRequestId(), "MixFlowRateServiceError {}", e);
        }
        return null;
    }

    public String getTargetUrl(RunTimeContext runTimeContext, String landingPage) {
        H5Request build = H5Request.builder()
                .requestId(runTimeContext.getRequestId())
                .pkg(null != runTimeContext.getAppModel() ? Optional.ofNullable(runTimeContext.getAppModel().getBundleId()).orElse("") : "")
                .ip(Optional.ofNullable(runTimeContext.getUserIpV4()).orElse(""))
                .ua(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""))
                .sltid(String.valueOf(runTimeContext.getPlacementId()))
                .build();
        String param = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.toJSONString(build).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(MONITOR_EVENT_HOST.get()).append("?s=").append(param);
        if (StringUtils.isNotBlank(landingPage)) {
            sb.append("&l=").append(Base64.getEncoder().withoutPadding().encodeToString(landingPage.getBytes()));
            LogUtils.logInfo(runTimeContext.getRequestId(), "add 302 step url {}", sb.toString());
        }
        LogUtils.logInfo(runTimeContext.getRequestId(), "mixFLow build {} and finalUrl {}", build, sb.toString());
        return sb.toString();
    }

}