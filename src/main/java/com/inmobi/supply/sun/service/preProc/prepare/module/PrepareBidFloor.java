package com.inmobi.supply.sun.service.preProc.prepare.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import cn.inmobi.pb.api.FloorEntityType;
import cn.inmobi.pb.api.RevenueShareType;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.component.model.redis.placement.RevShare;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.AuctionParam;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.constant.AuctionParam.ONE_HUNDRED;

/**
 * 底价计算
 *
 * @author Leo.Liao
 */
@Service
@Slf4j
public class PrepareBidFloor implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        PlacementModel placement = runTimeContext.getPlacementModel();
        long pe = placement.getPubExpectation();
        RevenueShareType revShareType = runTimeContext.getRevenueShareType();
        List<RevShare> revShareList = placement.getFixRevShareList();

        Map<FloorEntityType, BigDecimal> cosmosRevShare = revShareList.stream()
                .collect(Collectors.toMap(x -> FloorEntityType.forNumber(x.getRevShareType()), revShare -> revShare.getRevShare().divide(ONE_HUNDRED), (o1, o2) -> o1));

        // 百度特殊处理低价
        return Flux.fromIterable(Arrays.asList(FloorEntityType.values()))
                .filter(floorEntityType -> floorEntityType != FloorEntityType.UNKNOW_FLOOR_ENTITY_TYPE && floorEntityType != FloorEntityType.UNRECOGNIZED)
                .flatMap(floorEntityType -> {
                    if (!cosmosRevShare.keySet().contains(floorEntityType)
                            || cosmosRevShare.get(floorEntityType).compareTo(BigDecimal.valueOf(0)) == 0) {
                        BigDecimal cosmosRevShareValue;
                        if (revShareType.equals(RevenueShareType.MAX_REVENUE_SHARE)) {
                            //max revshare 默认1.2
                            cosmosRevShareValue = AuctionParam.DEFAULT_M_REV_SHARE;
                        } else {
                            //默认0.57
                            cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                            if (revShareType.equals(RevenueShareType.FIXED_REVENUE_SHARE)) {
                                if (floorEntityType == FloorEntityType.SATURN_FLOOR) {
                                    cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                                }
                                if (floorEntityType == FloorEntityType.JD_FLOOR
                                        || floorEntityType == FloorEntityType.DIDI_FLOOR
                                        || floorEntityType == FloorEntityType.GDT_FLOOR
                                        || floorEntityType == FloorEntityType.PDD_FLOOR
                                        || floorEntityType == FloorEntityType.JD_CUSTOM_FLOOR
                                        || floorEntityType == FloorEntityType.JD_FINANCE_RTB
                                        || floorEntityType == FloorEntityType.YIDIAN_RTB
                                        || floorEntityType == FloorEntityType.MEITUAN_RTB
                                        || floorEntityType == FloorEntityType.NEQUAL_RTB
                                        || floorEntityType == FloorEntityType.REACHMAX_RTB
                                        || floorEntityType == FloorEntityType.JD_ZL_RTB
                                        || floorEntityType == FloorEntityType.JULIANG_RTB
                                        || floorEntityType == FloorEntityType.MEITUANWAIMAI_RTB
                                ) {
                                    //这些默认是dsp all的rev share值
                                    if (cosmosRevShare.get(FloorEntityType.SATURN_FLOOR) != null
                                            && cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                        cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).multiply(ONE_HUNDRED);
                                    } else {
                                        cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                                    }
                                }
                                if (floorEntityType == FloorEntityType.JD_JM1_RTB
                                    /*|| floorEntityType == FloorEntityType.JD_ZL_RTB*/) {
                                    //这些默认是JD_CUSTOM_FLOOR的rev share值
                                    if (cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR) != null
                                            && cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                        cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR).multiply(ONE_HUNDRED);
                                    } else {
                                        cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                                    }
                                } else {
                                    if (cosmosRevShare.get(FloorEntityType.SATURN_FLOOR) != null
                                            && cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                        cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).multiply(ONE_HUNDRED);
                                    } else {
                                        cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                                    }
                                }
                            }
                        }
                        cosmosRevShare.put(floorEntityType, cosmosRevShareValue.divide(ONE_HUNDRED));
                        revShareList.add(new RevShare(floorEntityType.getNumber(), cosmosRevShareValue));
                    }
                    runTimeContext.getCosRevShare().put(floorEntityType, cosmosRevShare.get(floorEntityType));
                    String pubAccountId = (runTimeContext.getAppModel() != null) ? Optional.ofNullable(runTimeContext.getAppModel().getPubAccountId()).orElse("") : "";
                    runTimeContext.getFloorEntityMap().put(floorEntityType, getFixRevShareFloor(pe, cosmosRevShare.get(floorEntityType), revShareType));
                    long price = runTimeContext.getBaiduBrice();
                    if (revShareType.equals(RevenueShareType.AUTO_PILOT)) {
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.SATURN_FLOOR, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_FLOOR, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.DIDI_FLOOR, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.GDT_FLOOR, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.PDD_FLOOR, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.NEQUAL_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.REACHMAX_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.MEITUAN_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.YIDIAN_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_FINANCE_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.MEITUANWAIMAI_RTB, getApRTBDFloor(pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_CUSTOM_FLOOR, getApRTBDFloor(pe)
                        );
                    }
                    if (pubAccountId.equals(DynamicStringConfig.BAIDU_ACCOUNT.get()) && price > 0L) {
                        runTimeContext.getFloorEntityMap().put(floorEntityType, price);
                    }
//                    log.info("PrepareBidFloor requestId {} floorEntityType {} price {} {}", runTimeContext.getRequestId(), floorEntityType, price, runTimeContext.getFloorEntityMap());
                    return Mono.just(0);
                }).last()
                .onErrorResume(throwable -> {
                    LogUtils.logError(runTimeContext.getRequestId(),"PrepareBidFloor error {}", throwable);
                    return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, throwable));
                });
    }

    private Long getFixRevShareFloor(Long pe, BigDecimal revShare, RevenueShareType revenueShareType) {
        if (revenueShareType.equals(RevenueShareType.MAX_REVENUE_SHARE)) {
            // pe/1.2 * 0.95
            return (BigDecimal.valueOf(pe).divide(revShare == null ? new BigDecimal(DynamicStringConfig.DEFAULT_M_REV_SHARE.get()) : revShare, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get()))).longValue();
        }
        // pe/revshare * 0.95
        return (BigDecimal.valueOf(pe).divide(revShare == null ? new BigDecimal(DynamicStringConfig.DEFAULT_REV_SHARE.get()) : revShare, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get()))).longValue();
    }

    private Long getApRTBDFloor(Long pe) {
        //pe / 0.95
        return (BigDecimal.valueOf(pe).divide(new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get()), RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)).longValue();
    }
}
