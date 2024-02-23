package com.inmobi.supply.sun.service.preProc.prepare.module;

import java.util.*;
import java.util.stream.Collectors;

import cn.inmobi.pb.api.SourceType;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.placement.PlacementInExModel;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.framework.common.utils.SpringUtils;
import com.inmobi.supply.sun.adPool.IAdPool;
import com.inmobi.supply.sun.adPool.PerfAdPool;
import com.inmobi.supply.sun.adPool.SaturnAdPool;
import com.inmobi.supply.sun.constant.AdPoolForwardScaleConstant;
import com.inmobi.supply.sun.constant.placement.InExLevel;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 获取请求的adPool
 *
 * @author remi.li
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class PrepareAdPools implements ModuleWorker<Integer> {
    @Autowired
    private PerfAdPool perfAdPool;
    @Autowired
    private SaturnAdPool saturnAdPool;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"PrepareAdPools");
            Map<SourceType, IAdPool> adPoolMap = SpringUtils.getBeansOfType(IAdPool.class).values()
                    .stream()
                    .collect(Collectors.toMap(IAdPool::sourceType, iAdpool -> iAdpool, (o1, o2) -> o1));
            if (MapUtils.isEmpty(adPoolMap)) {
                //availableAdPool is empty
                throw new SunException(TermCode.NO_AVAILABLE_AD_POOL, runTimeContext);
            }
            /*if (runTimeContext.getPlacementModel().getIsTestPlacement() != null
                    && runTimeContext.getPlacementModel().getIsTestPlacement() == 1) {
                log.info("testPlacement={}", runTimeContext.getPlacementId());
                //测试广告位
                throw new SunException(TermCode.NO_AVAILABLE_AD_POOL, runTimeContext);
            }*/
            if (runTimeContext.getTestTraffic()) {
                runTimeContext.getAdPoolList().addAll(adPoolMap.values());
                return Mono.just(0);
            }
            PlacementModel placement = runTimeContext.getPlacementModel();
            List<PlacementInExModel> placeLevelList = Optional.ofNullable(placement.getPlaceLevel()).orElse(new ArrayList<>());
            List<PlacementInExModel> appLevelList = Optional.ofNullable(placement.getAppLevel()).orElse(new ArrayList<>());
            List<PlacementInExModel> pubAccountLevelList = Optional.ofNullable(placement.getPubAccountIdLevel()).orElse(new ArrayList<>());

            Map<String, List<PlacementInExModel>> levelMap = new HashMap<>();
            levelMap.put("placement", placeLevelList);
            levelMap.put("app", appLevelList);
            levelMap.put("pubAccount", pubAccountLevelList);

            if (CollectionUtils.isEmpty(placeLevelList)
                    && CollectionUtils.isEmpty(appLevelList)
                    && CollectionUtils.isEmpty(pubAccountLevelList)) {
                runTimeContext.getAdPoolList().addAll(adPoolMap.values());
                return Mono.just(0);
            }

            Map<Integer, Collection<String>> incluMap = new HashMap<>(); //advertiserId incluMap
            Map<Integer, Collection<String>> excluMap = new HashMap<>(); //advertiserId excluMap
            Map<String, Integer> forwardScale = new HashMap<>(); // adpool forwardScale

            return Flux.fromIterable(levelMap.entrySet())
                    .flatMap(placementInExModelList -> Flux.fromIterable(placementInExModelList.getValue()).flatMap(placementInExModel -> {
                if (placementInExModelList.getKey().equals("placement") && placementInExModel.getLevel() == InExLevel.AD_POOL.getValue()) {
                    forwardScale.putAll(Optional.ofNullable(placementInExModel.getForwardScale()).orElse(new HashMap<>()));
                }
                if (placementInExModel.getLevel() != null
                        && placementInExModel.getLevel() == InExLevel.ADVERTISER.getValue()
                        && CollectionUtils.isNotEmpty(placementInExModel.getInclusions())) {
                    Collection<String> newIncluList = Optional.ofNullable(placementInExModel.getInclusions()).orElse(Collections.emptyList());

                    if (!incluMap.containsKey(placementInExModel.getLevel())) {
                        incluMap.put(placementInExModel.getLevel(), newIncluList);
                    } else {
                        incluMap.computeIfPresent(placementInExModel.getLevel(), (key, exist) -> {
                            //取交集
                            return CollectionUtils.intersection(exist, newIncluList);
                        });
                    }
                }
                if (placementInExModel.getLevel() != null
                        && placementInExModel.getLevel() == InExLevel.ADVERTISER.getValue()
                        && CollectionUtils.isNotEmpty(placementInExModel.getExclusions())) {
                    Collection<String> newExcluList = Optional.ofNullable(placementInExModel.getExclusions()).orElse(Collections.emptyList());
                    //取并集
                    if (!excluMap.containsKey(placementInExModel.getLevel())) {
                        excluMap.put(placementInExModel.getLevel(), newExcluList);
                    } else {
                        excluMap.computeIfPresent(placementInExModel.getLevel(), (key, exist) -> {
                            //取并集
                            return CollectionUtils.union(exist, newExcluList);
                        });
                    }
                }
                return Mono.just(0);
            })).last().flatMap(integer -> Flux.fromIterable(Arrays.asList(InExLevel.values()))
                        .flatMap(inExLevel -> {
                            if (inExLevel == InExLevel.AD_POOL) {
                                //取决于forwardScale
                                if (MapUtils.isEmpty(forwardScale)) {
                                    runTimeContext.getAdPoolList().addAll(adPoolMap.values());
                                    return Mono.just(0);
                                } else {
                                    return Flux.fromIterable(forwardScale.entrySet()).flatMap(entry -> {
                                        int forwardProportion = Math.min(entry.getValue(), 100);
                                        forwardProportion = Math.max(forwardProportion, 0);
                                        AdPoolForwardScaleConstant adPoolForwardScaleConstant = AdPoolForwardScaleConstant.of(entry.getKey());
                                        Random random = new Random();
                                        int randomTraffic = random.nextInt(100);
                                        switch (adPoolForwardScaleConstant) {
                                            case PERF:
                                                if (adPoolMap.containsKey(SourceType.PERF)
                                                        && forwardProportion > 0
                                                        && forwardProportion >= randomTraffic) {
                                                    runTimeContext.getAdPoolList().add(adPoolMap.get(SourceType.PERF));
                                                }
                                                break;
                                            case BRAND:
                                                break;
                                            case RTBD_ALL:
                                                if (adPoolMap.containsKey(SourceType.SATURN)
                                                        && forwardProportion > 0
                                                        && forwardProportion >= randomTraffic) {
                                                    runTimeContext.getAdPoolList().add(adPoolMap.get(SourceType.SATURN));
                                                }
                                                break;
                                        }
                                        return Mono.just(0);
                                    });
                                }
                            }
                            if (inExLevel == InExLevel.ADVERTISER) {
                                Collection<String> inclusionList = incluMap.getOrDefault(inExLevel.getValue(), new ArrayList<>());
                                Collection<String> exclusionList = excluMap.getOrDefault(inExLevel.getValue(), new ArrayList<>());
                                if (CollectionUtils.isEmpty(inclusionList)) {
                                    if (CollectionUtils.isNotEmpty(exclusionList)) {
                                        return Flux.fromIterable(exclusionList).flatMap(exclusion -> {
                                            try {
                                                if (StringUtils.isNumeric(exclusion)) {
                                                    runTimeContext.getExcludeAdvertiserIds().add(Long.valueOf(exclusion));
                                                } else {
                                                    runTimeContext.getExcludeChannelIds().add(exclusion);
                                                }
                                            } catch (Exception e) {
                                                log.debug("placementId, prepare adPools error,inclusion={} {}",
                                                        runTimeContext.getPlacementId(), inclusionList, exclusionList);
                                            }
                                            return Mono.just(0);
                                        });
                                    } else {
                                        return Mono.just(0);
                                    }
                                } else {
                                    //定投
                                    return Flux.fromIterable(inclusionList).flatMap(inclusion -> {
                                        try {
                                            if (StringUtils.isNumeric(inclusion)) {
                                                runTimeContext.getIncludeAdvertiserIds().add(Long.valueOf(inclusion));
                                            } else {
                                                runTimeContext.getIncludeChannelIds().add(inclusion);
                                            }
                                        } catch (Exception e) {
                                            log.debug("placementId, prepare adPools error,inclusion={} {}",
                                                    runTimeContext.getPlacementId(), inclusionList, exclusionList);
                                        }
                                        return Mono.just(0);
                                    }).thenMany(Flux.fromIterable(exclusionList).defaultIfEmpty("").flatMap(exclusion -> {
                                        try {
                                            if (StringUtils.isNotBlank(exclusion)) {
                                                if (StringUtils.isNumeric(exclusion)) {
                                                    runTimeContext.getExcludeAdvertiserIds().add(Long.valueOf(exclusion));
                                                } else {
                                                    runTimeContext.getExcludeChannelIds().add(exclusion);
                                                }
                                            }
                                        } catch (Exception e) {
                                            log.debug("placementId, prepare adPools error,inclusion={} {}",
                                                    runTimeContext.getPlacementId(), inclusionList, exclusionList);
                                        }
                                        return Flux.just(0);
                                    }));
                                }
                            }
                            return Mono.just(0);
                        }).last())
                        .map(integer -> {
                            if (CollectionUtils.isEmpty(runTimeContext.getIncludeAdvertiserIds())
                                    && CollectionUtils.isNotEmpty(runTimeContext.getIncludeChannelIds())) {
                                runTimeContext.getAdPoolList().remove(perfAdPool);
                            }
                            if (CollectionUtils.isEmpty(runTimeContext.getIncludeChannelIds())
                                    && CollectionUtils.isNotEmpty(runTimeContext.getIncludeAdvertiserIds())) {
                                runTimeContext.getAdPoolList().remove(saturnAdPool);
                            }

                            if (CollectionUtils.isEmpty(runTimeContext.getAdPoolList())) {
                                throw Exceptions.propagate(new SunException(TermCode.NO_AVAILABLE_AD_POOL, runTimeContext));
                            }
                            return integer;
                    })
                    .onErrorResume(throwable -> {
                        if (throwable instanceof SunException) {
                            throw Exceptions.propagate(throwable);
                        } else {
                            LogUtils.logError(runTimeContext.getRequestId(),"prepare adPools error {}", throwable);
                            throw Exceptions.propagate(new SunException(TermCode.SERVER_ERROR, runTimeContext, throwable));
                        }
                    });
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}