package com.inmobi.supply.sun.repo.redis.service;


import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.DynamicFloorRedisImpl;
import com.inmobi.supply.sun.repo.redis.impl.PlacementRedisImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author remi.li
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class DynamicFloorRepo implements BaseRepo<Integer> {
    private static final long DYNAMIC_FLOOR_LOAD_INTERVAL_SECONDS = 90L;
    @Autowired
    DynamicFloorRedisImpl dynamicFloorRedis;
    @Autowired
    private DynamicConfigService dynamicConfigService;
    @Getter
    private volatile Map<PlacementHour, Long> placeRaisedEcpmCache;
    @Getter
    private volatile Map<PlacementHour, Long> placeDeclinedCache;
    @Getter
    private volatile Map<PlacementHour, Long> placeEcpmNumCache;
    @Getter
    private volatile Map<String, ExpClickCharge> priceLocationExpClickCache;
    @Autowired
    PlacementRedisImpl placementRedis;

    @Override
    public int size() {
        return Optional.ofNullable(priceLocationExpClickCache).orElse(new HashMap<>()).size();
    }

    @Override
    public Flux<Integer> loadRepo() {
        /*List<Long> expPlacementIds = dynamicConfigService.getConfig("application").getProperty("dynamic.floor.exp.placements", new Function<String, List<Long>>() {
            @Nullable
            @Override
            public List<Long> apply(@Nullable String input) {
                try {
                    return (List<Long>) ObjectMapperUtils.fromJSON(input, List.class);
                } catch (Exception e) {
                    log.error("DynamicMapBooleanConfig get error", e);
                    return Collections.EMPTY_LIST;
                }
            }
        }, Collections.EMPTY_LIST);

        Map<PlacementHour, Long> tempPlaceRaisedEcpmCache = new HashMap<>();
        Map<PlacementHour, Long> tempPlaceDeclinedCache = new HashMap<>();
        Map<PlacementHour, Long> tempPlaceEcpmNumCache = new HashMap<>();

        Map<String, ExpClickCharge> tempPriceLocationExpClickCache = new HashMap<>();
        return placementRedis.findAll()
                .filter(placementModel -> placementModel.getType() == AdType.FEED_VALUE).flatMap(new Function<PlacementModel, Flux<Integer>>() {
            @Nullable
            @Override
            public Flux<Integer> apply(@NonNull PlacementModel placementModel) {
                long placementId = placementModel.getId();
                return Flux.fromIterable(jdBidPriceLocation.keySet()).flatMap(new Function<Integer, Mono<Integer>>() {
                    @Nullable
                    @Override
                    public Mono<Integer> apply(@Nullable Integer priceLocation) {
                        return dynamicFloorRedis.findByMultiId(
                                RAISED_EXP_COUNT.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                RAISED_CLICK_COUNT.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                RAISED_CHARGE.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                RAISED_PAY.of(String.valueOf(placementId), String.valueOf(priceLocation)),

                                DECLINED_EXP_COUNT.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                DECLINED_CLICK_COUNT.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                DECLINED_CHARGE.of(String.valueOf(placementId), String.valueOf(priceLocation)),
                                DECLINED_PAY.of(String.valueOf(placementId), String.valueOf(priceLocation)))
                                .flatMap(new Function<List<String>, Mono<Integer>>() {
                                    @Nullable
                                    @Override
                                    public Mono<Integer> apply(@Nullable List<String> redisValueList) {
                                        if (CollectionUtils.isNotEmpty(redisValueList)) {
                                            if (StringUtils.isNotBlank(redisValueList.get(0))
                                                    || StringUtils.isNotBlank(redisValueList.get(1))
                                                    || StringUtils.isNotBlank(redisValueList.get(2))
                                                    || StringUtils.isNotBlank(redisValueList.get(3))
                                                    || StringUtils.isNotBlank(redisValueList.get(4))
                                                    || StringUtils.isNotBlank(redisValueList.get(5))
                                                    || StringUtils.isNotBlank(redisValueList.get(6))
                                                    || StringUtils.isNotBlank(redisValueList.get(7))) {
                                                ExpClickCharge expClickCharge = new ExpClickCharge(
                                                        Long.parseLong(StringUtils.isNotBlank(redisValueList.get(0)) ? redisValueList.get(0) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(1)) ? redisValueList.get(1) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(2)) ? redisValueList.get(2) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(3)) ? redisValueList.get(3) : "0"),

                                                        Long.parseLong(StringUtils.isNotBlank(redisValueList.get(4)) ? redisValueList.get(4) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(5)) ? redisValueList.get(5) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(6)) ? redisValueList.get(6) : "0"),
                                                        Double.parseDouble(StringUtils.isNotBlank(redisValueList.get(7)) ? redisValueList.get(7) : "0"));
                                                tempPriceLocationExpClickCache.put(placementId + "_" + priceLocation, expClickCharge);
                                            }
                                        }
                                        return Mono.just(0);
                                    }
                                });
                    }
                });
            }
        }).last().flatMap(integer -> {
            priceLocationExpClickCache = tempPriceLocationExpClickCache;
            return Mono.just(0);
        }).flux().onErrorResume(exception -> {
            log.error("DynamicFloorRepo loadRepo error:" + exception.getMessage());
            return Flux.just(0);
        });*/
        return Flux.just(0);
    }

    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(DYNAMIC_FLOOR_LOAD_INTERVAL_SECONDS);
    }

    @AllArgsConstructor
    public static class PlacementHour {
        long placementId;
        int hour;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ExpClickCharge {
        long raisedExpCount;
        double raisedClickCount;
        double raisedCharge;
        double raisedPay;

        long declinedExpCount;
        double declinedClickCount;
        double declinedCharge;
        double declinedPay;
    }


}
