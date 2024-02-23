package com.inmobi.supply.sun.service.preProc.prepare.module;

import cn.inmobi.pb.api.FloorEntityType;
import cn.inmobi.pb.api.RevenueShareType;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.component.model.redis.placement.RevShare;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.AuctionParam;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.inmobi.supply.sun.constant.AuctionParam.ONE_HUNDRED;

/**
 * 底价计算
 *
 * @author Leo.Liao
 */
@Service
@Slf4j
public class PrepareBidFloor2 implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(),"PrepareBidFloor2");
        PlacementModel placement = runTimeContext.getPlacementModel();
        long pe = placement.getPubExpectation();
        RevenueShareType revShareType = runTimeContext.getRevenueShareType();
        List<RevShare> revShareList = placement.getFixRevShareList();

        /**
         * 不同渠道分成
         */
        Map<FloorEntityType, BigDecimal> cosmosRevShare = revShareList.stream()
                .collect(Collectors.toMap(x -> FloorEntityType.forNumber(x.getRevShareType()), revShare -> revShare.getRevShare().divide(ONE_HUNDRED), (o1, o2) -> o1));
        LogUtils.logInfo(runTimeContext.getRequestId(),"cosmosRevShareInfo {}",JSON.toJSONString(cosmosRevShare));

        boolean readPubFloor = false;
        if(null != runTimeContext.getBaseRequest()
                && runTimeContext.getBaseRequest().getImp().getPriceFloor() > 0
                && revShareType.equals(RevenueShareType.FIXED_REVENUE_SHARE)
                && ((runTimeContext.getRequestType().equals(RequestType.API_4_0) && runTimeContext.getPlacementModel().getFloorPriceFlag() == 1) || runTimeContext.getRequestType().equals(RequestType.CUSTOM_API))){
            // 只针对 adapter 和 API V4 生效
            readPubFloor = true;
            LogUtils.logInfo(runTimeContext.getRequestId(),"readPubFloorIsTrue");
        }
        boolean finalReadPubFloor = readPubFloor;
        /**
         * 不同渠道pe
         */
        Map<FloorEntityType, Long> cosmosRevSharePe = revShareList.stream()
                    .collect(Collectors.toMap(x -> FloorEntityType.forNumber(x.getRevShareType()), revShare -> {
                        if(runTimeContext.getOrtbBidFloor() != null && DynamicListConfig.ORTB_PUBLISHER_LIST.get().contains(runTimeContext.getPlacementModel().getAccountId())){
                            return runTimeContext.getOrtbBidFloor();
                        }else if(finalReadPubFloor) {
                            PerfUtils.perf("sun.floor.price", "placement", String.valueOf(placement.getId()));
                            LogUtils.logInfo(runTimeContext.getRequestId(),"prepareBidFloor2 floorPrice {}",runTimeContext.getBaseRequest().getImp().getPriceFloor());
                            return  (long) runTimeContext.getBaseRequest().getImp().getPriceFloor();
                        }else if (null == revShare.getPubExpectation() || revShare.getPubExpectation() <= 0) {
                            return pe;
                        } else {
                            return revShare.getPubExpectation();
                        }
                    }, (o1, o2) -> o1));
        runTimeContext.setCosmosRevSharePe(cosmosRevSharePe);
        LogUtils.logInfo(runTimeContext.getRequestId(), "cosmosRevSharePe {}", JSON.toJSONString(cosmosRevSharePe));

        // 百度特殊处理低价
        Map<FloorEntityType, Long> finalCosmosRevSharePe = cosmosRevSharePe;
        return Flux.fromIterable(Arrays.asList(FloorEntityType.values()))
                .filter(floorEntityType -> floorEntityType != FloorEntityType.UNKNOW_FLOOR_ENTITY_TYPE && floorEntityType != FloorEntityType.UNRECOGNIZED)
                .flatMap(floorEntityType -> {
                    if (!cosmosRevShare.keySet().contains(floorEntityType)
                            || cosmosRevShare.get(floorEntityType).compareTo(BigDecimal.valueOf(0)) == 0) {
                        //默认0.57
                        BigDecimal cosmosRevShareValue = AuctionParam.DEFAULT_REV_SHARE;
                        if (revShareType.equals(RevenueShareType.FIXED_REVENUE_SHARE)) {
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
                                    || floorEntityType == FloorEntityType.GDT_RTB_CUSTOM
                                    || floorEntityType == FloorEntityType.DomobRTB_PB
                                    || floorEntityType == FloorEntityType.NETEASE_RTB_PB
                                    || floorEntityType == FloorEntityType.REACHMAXRTB_YUNLIN_PB
                                    || floorEntityType == FloorEntityType.JD_RTB_REVENUE_SHARE
                                    || floorEntityType == FloorEntityType.KUAISHOU_RTB_CUSTOM
                                    || floorEntityType == FloorEntityType.AliRTB
                                    || floorEntityType == FloorEntityType.baiduRTB_Custom
                                    || floorEntityType == FloorEntityType.JD_Liande_RTB
                            ) {
                                //这些默认是dsp all的rev share值
                                if (cosmosRevShare.get(FloorEntityType.SATURN_FLOOR) != null
                                        && cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                    cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).multiply(ONE_HUNDRED);
                                }
                            }
                            if (floorEntityType == FloorEntityType.JD_JM1_RTB) {
                                //这些默认是JD_CUSTOM_FLOOR的rev share值
                                if (cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR) != null
                                        && cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                    cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.JD_CUSTOM_FLOOR).multiply(ONE_HUNDRED);
                                }
                            } else {
                                if (cosmosRevShare.get(FloorEntityType.SATURN_FLOOR) != null
                                        && cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).compareTo(BigDecimal.valueOf(0)) != 0) {
                                    cosmosRevShareValue = cosmosRevShare.get(FloorEntityType.SATURN_FLOOR).multiply(ONE_HUNDRED);
                                }
                            }
                        }

                        if(revShareType.equals(RevenueShareType.PD_REVENUE_SHARE)){
                            BigDecimal revShare = placement.getRevShare();
                            if(revShare.compareTo(BigDecimal.valueOf(0)) == 0){
                                // PD模式 默认0.80
                                cosmosRevShareValue = AuctionParam.DEFAULT_PD_REV_SHARE;
                            }else {
                                cosmosRevShareValue = placement.getRevShare();
                                LogUtils.logInfo(runTimeContext.getRequestId(),"prepareBidFloor2 pdRevShare {}, placementId {}",placement.getRevShare(),placement.getId());
                            }
                        }
                        cosmosRevShare.put(floorEntityType, cosmosRevShareValue.divide(ONE_HUNDRED));
                        finalCosmosRevSharePe.put(floorEntityType, pe);
                        revShareList.add(new RevShare(floorEntityType.getNumber(), cosmosRevShareValue));
                    }


                    runTimeContext.getCosRevShare().put(floorEntityType, cosmosRevShare.get(floorEntityType));
                    String pubAccountId = (runTimeContext.getPlacementModel() != null) ? Optional.ofNullable(runTimeContext.getPlacementModel().getAccountId()).orElse("") : "";
                    Long realPe = finalCosmosRevSharePe.get(floorEntityType);
                    if (null == realPe) {
                        realPe = pe;
                    }
                    runTimeContext.getFloorEntityMap().put(floorEntityType, getFixRevShareFloor(realPe, cosmosRevShare.get(floorEntityType), finalReadPubFloor));
                    long price = runTimeContext.getBaiduBrice();
                    if (revShareType.equals(RevenueShareType.AUTO_PILOT)) {
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.SATURN_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.SATURN_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.JD_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.DIDI_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.DIDI_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.GDT_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.GDT_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.PDD_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.PDD_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.NEQUAL_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.NEQUAL_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.REACHMAX_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.REACHMAX_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.MEITUAN_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.MEITUAN_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.YIDIAN_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.YIDIAN_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_FINANCE_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.JD_FINANCE_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.MEITUANWAIMAI_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.MEITUANWAIMAI_RTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_CUSTOM_FLOOR, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.JD_CUSTOM_FLOOR), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.GDT_RTB_CUSTOM, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.GDT_RTB_CUSTOM), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.DomobRTB_PB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.DomobRTB_PB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.NETEASE_RTB_PB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.NETEASE_RTB_PB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.REACHMAXRTB_YUNLIN_PB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.REACHMAXRTB_YUNLIN_PB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_RTB_REVENUE_SHARE, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.JD_RTB_REVENUE_SHARE), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.KUAISHOU_RTB_CUSTOM, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.KUAISHOU_RTB_CUSTOM), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.AliRTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.AliRTB), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.baiduRTB_Custom, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.baiduRTB_Custom), pe));
                        runTimeContext.getFloorEntityMap().put(FloorEntityType.JD_Liande_RTB, getApRTBDFloor(finalCosmosRevSharePe.get(FloorEntityType.JD_Liande_RTB), pe));
                    }
                    if (pubAccountId.equals(DynamicStringConfig.BAIDU_ACCOUNT.get()) && price > 0L) {
                        runTimeContext.getFloorEntityMap().put(floorEntityType, price);
                    }
                    return Mono.just(0);
                }).last()
                .onErrorResume(throwable -> {
                    LogUtils.logError(runTimeContext.getRequestId(), "PrepareBidFloor2 error {}", throwable);
                    return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, throwable));
                });
    }

    private Long getFixRevShareFloor(Long pe, BigDecimal revShare, boolean finalReadPubFloor) {
        if (finalReadPubFloor) {
            // pe/revshare
            return (BigDecimal.valueOf(pe).divide(revShare == null ? new BigDecimal(DynamicStringConfig.DEFAULT_REV_SHARE.get()) : revShare, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)).longValue();
        } else {
            // pe/revshare * 0.95
            return (BigDecimal.valueOf(pe).divide(revShare == null ? new BigDecimal(DynamicStringConfig.DEFAULT_REV_SHARE.get()) : revShare, RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get()))).longValue();
        }
    }

    private Long getApRTBDFloor(Long realPe, Long defaultPe) {
        if (null == realPe) {
            realPe = defaultPe;
        }
        //pe / 0.95
        return (BigDecimal.valueOf(realPe).divide(new BigDecimal(DynamicStringConfig.DEFAULT_MAX_REV_SHARE.get()), RuntimeConstant.BIGDECIMAL_SCALE, RoundingMode.HALF_UP)).longValue();
    }
}