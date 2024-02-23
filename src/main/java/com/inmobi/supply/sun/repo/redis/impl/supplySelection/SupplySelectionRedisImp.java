package com.inmobi.supply.sun.repo.redis.impl.supplySelection;

import cn.inmobi.pb.api.SourceType;
import com.inmobi.supply.sun.constant.RedisKey;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author remi.li
 */
//@Repository
public class SupplySelectionRedisImp {
    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    /**
     * get DeviceId RequestSum
     * @param deviceId
     * @return
     */
    public Mono<String> getDeviceIdRequestSum(String deviceId) {
        return stringTemplate.opsForValue()
                .get(RedisKey.DEVICEID_REQUEST_PREFIX.of(deviceId))
                .filter(StringUtils::isNotBlank);
    }

    public Mono<String> getGradingByPlacementSourceType(long placementId, int sourceType) {
        return stringTemplate.opsForValue()
                .get(RedisKey.SUPPLY_SELECTION_GRADE_TAG.of(placementId + "_" + sourceType))
                .filter(StringUtils::isNotBlank);
    }

    public Mono<Boolean> saveGrade(long placementId, int sourceType, String tag) {
        return stringTemplate.opsForValue()
                .set(RedisKey.SUPPLY_SELECTION_GRADE_TAG.of(placementId + "_" + sourceType), tag);
    }

    public Mono<String> getRecoveryGradeBeginDate(long placementId, int sourceType) {
        return stringTemplate.opsForValue()
                .get(RedisKey.SUPPLY_SELECTION_RECOVERY_DATE.of(placementId + "_" + sourceType));
    }

    public Mono<Boolean> saveRecoveryGradeBeginDate(long placementId, int sourceType, String date) {
        return stringTemplate.opsForValue()
                .set(RedisKey.SUPPLY_SELECTION_RECOVERY_DATE.of(placementId + "_" + sourceType), date);
    }

    public Mono<Boolean> delGrade(long placementId, int sourceType) {
        return stringTemplate.opsForValue().delete(RedisKey.SUPPLY_SELECTION_GRADE_TAG.of(placementId + "_" + sourceType));
    }

    /**
     * get MarketRevenueSum by date
     * key = bsmrsp_date
     * value = marketbidSum
     * date
     * @param date
     * @return
     */
    public Mono<String> getMarketRevenueSumByDate(String date) {
        return stringTemplate.opsForValue()
                .get(RedisKey.REVENUE_MARKET_SUM_PREFIX.of(date))
                .filter(StringUtils::isNotBlank);
    }

    /**
     * get MarketBidSum and marketRequestSum by date
     * key = bsmrsp_date
     * value = marketBidSum_marketRequestSum
     * date
     * @param date
     * @return
     */
    public Mono<String> getMarketBidSumByDate(String date) {
        return stringTemplate.opsForValue()
                .get(RedisKey.BID_SUM_MARKET_REQUEST_SUM_PREFIX.of(date))
                .filter(StringUtils::isNotBlank);
    }

    /**
     * get get PlacementIdSourceType BidSum and requestSum By Date
     * key = bsrsp_date_placementId_sourceType
     * value = BidSum_requestSum
     * @param date
     * @param placementId
     * @param sourceType
     * @return
     */
    public Mono<String> getPlacementIdSourceTypeBidSumRequestSumByDate(String date, String placementId, String sourceType) {
        return stringTemplate.opsForValue()
                .get(RedisKey.BID_SUM_MARKET_REQUEST_SUM_PREFIX.of(date + "_" + placementId + "_" + sourceType))
                .filter(StringUtils::isNotBlank);
    }

    /**
     * get get PlacementIdSourceType BidSum and requestSum By Date
     * key = rsp_date_placementId_sourceType
     * value = revenueSum
     * @param date
     * @param placementId
     * @param sourceType
     * @return
     */
    public Mono<String> getPlacementIdSourceTypeRevenueSumByDate(String date, String placementId, String sourceType) {
        return stringTemplate.opsForValue()
                .get(RedisKey.REVENUE_SUM_PREFIX.of(date + "_" + placementId + "_" + sourceType))
                .filter(StringUtils::isNotBlank);
    }

    /**
     *
     * @param date
     * @return
     */
    public Flux<Tuple4<String, String, String, String>> findAllPlacementIdSourceTypeBidSumRequestSumByDate(String date) {
        return stringTemplate.opsForSet()
                .members(RedisKey.PLACEMENT_INFO_SET.getPrefix())
                .flatMap(new Function<String, Flux<Tuple4<String, String, String, String>>>() {
                    @Override
                    public Flux<Tuple4<String, String, String, String>> apply(String placementId) {
                       return Flux.fromIterable(Arrays.stream(SourceType.values())
                                .filter(sourceType -> sourceType != SourceType.UNKONW_SOURCE_TYPE && sourceType != SourceType.UNRECOGNIZED)
                                .collect(Collectors.toList()))
                                .flatMap(new Function<SourceType, Mono<Tuple4<String, String, String, String>>> () {
                                    @Override
                                    public Mono<Tuple4<String, String, String, String>> apply(SourceType sourceType) {
                                        return getPlacementIdSourceTypeBidSumRequestSumByDate(date, placementId, String.valueOf(sourceType.getNumber())).flatMap(new Function<String, Mono<Tuple4<String, String, String, String>>>() {
                                            @Override
                                            public Mono<Tuple4<String, String, String, String>> apply(String sum) {
                                                return Mono.just(Tuples.of(date, placementId, String.valueOf(sourceType.getNumber()), sum));
                                            }
                                        });
                                    }
                                });
                    }
                });
    }

    public Flux<Tuple4<String, String, String, String>> findAllPlacementIdSourceTypeRevenueSumByDate(String date) {
        return stringTemplate.opsForSet()
                .members(RedisKey.PLACEMENT_INFO_SET.getPrefix())
                .flatMap(new Function<String, Flux<Tuple4<String, String, String, String>>>() {
                    @Override
                    public Flux<Tuple4<String, String, String, String>> apply(String placementId) {
                        return Flux.fromIterable(Arrays.stream(SourceType.values())
                                .filter(sourceType -> sourceType != SourceType.UNKONW_SOURCE_TYPE && sourceType != SourceType.UNRECOGNIZED)
                                .collect(Collectors.toList()))
                                .flatMap(new Function<SourceType, Mono<Tuple4<String, String, String, String>>>() {
                                    @Override
                                    public Mono<Tuple4<String, String, String, String>> apply(SourceType sourceType) {
                                        return getPlacementIdSourceTypeRevenueSumByDate(date, placementId, String.valueOf(sourceType.getNumber())).flatMap(new Function<String, Mono<Tuple4<String, String, String, String>>>() {
                                            @Override
                                            public Mono<Tuple4<String, String, String, String>> apply(String sum) {
                                                return Mono.just(Tuples.of(date, placementId, String.valueOf(sourceType.getNumber()), sum));
                                            }
                                        });
                                    }
                                });
                    }
                });
    }
}