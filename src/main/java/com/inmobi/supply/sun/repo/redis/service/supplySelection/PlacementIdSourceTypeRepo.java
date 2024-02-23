package com.inmobi.supply.sun.repo.redis.service.supplySelection;

import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.supplySelection.SupplySelectionRedisImp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.inmobi.supply.sun.utils.WorkerUtils.nDaysBefore;

//@Service
@Slf4j
public class PlacementIdSourceTypeRepo implements BaseRepo<Object> {
    private volatile Map<Tuple3<String, String, String>, String> placementIdSourceTypeBidSumRequestSum = new HashMap<>(); // <date, placementId, sourceType> bidSum_requestSum>>
    private volatile Map<Tuple3<String, String, String>, String> placementIdSourceTypeRevenueSum = new HashMap<>(); // <date, placementId, sourceType>, revenueSum>>

    @Autowired
    private SupplySelectionRedisImp supplySelectionRedisImp;

    @Override
    public Publisher<Object> loadRepo() {
        Map<Tuple3<String, String, String>, String> newPlacementIdSourceTypeBidSumRequestSum = new HashMap<>();
        Map<Tuple3<String, String, String>, String> newPlacementIdSourceTypeRevenueSumRequestSum = new HashMap<>(); // <date, <placementId, sourceType, requestSum>>

        return Flux.fromIterable(nDaysBefore(7)).flatMap(new Function<String, Flux<String>>() {
            @Override
            public Flux<String> apply(String date) {
                Flux<Tuple4<String, String, String, String>> bidSumRequestSumFlux = supplySelectionRedisImp.findAllPlacementIdSourceTypeBidSumRequestSumByDate(date);
                Flux<Tuple4<String, String, String, String>> revenueSumFlux = supplySelectionRedisImp.findAllPlacementIdSourceTypeRevenueSumByDate(date);
                return bidSumRequestSumFlux.zipWith(revenueSumFlux).flatMap(new Function<Tuple2<Tuple4<String, String, String, String>, Tuple4<String, String, String, String>>, Mono<String>>() {
                    @Override
                    public Mono<String> apply(Tuple2<Tuple4<String, String, String, String>, Tuple4<String, String, String, String>> tuple2) {
                        newPlacementIdSourceTypeBidSumRequestSum.put(Tuples.of(tuple2.getT1().getT1(), tuple2.getT1().getT2(), tuple2.getT1().getT3()), tuple2.getT1().getT4());
                        newPlacementIdSourceTypeRevenueSumRequestSum.put(Tuples.of(tuple2.getT2().getT1(), tuple2.getT2().getT2(), tuple2.getT2().getT3()), tuple2.getT2().getT4());
                        return Mono.just("");
                    }
                }).thenMany(Flux.just("").flatMap(integer -> {
                    placementIdSourceTypeBidSumRequestSum = newPlacementIdSourceTypeBidSumRequestSum;
                    placementIdSourceTypeRevenueSum = newPlacementIdSourceTypeRevenueSumRequestSum;
                    return Flux.just("");
                }));
            }
        });
    }

    public long getBidSum(String date, long placementId, int sourceType) {
        String bidSumRequestSum = placementIdSourceTypeBidSumRequestSum.getOrDefault(Tuples.of(date, String.valueOf(placementId), String.valueOf(sourceType)), "");
        if (StringUtils.isNotBlank(bidSumRequestSum) && StringUtils.split(bidSumRequestSum, "_").length == 2) {
            return Long.parseLong(StringUtils.split(bidSumRequestSum, "_")[0]);
        } else {
            return 0;
        }
    }

    public long getRequestSum(String date, long placementId, int sourceType) {
        String bidSumRequestSum = placementIdSourceTypeBidSumRequestSum.getOrDefault(Tuples.of(date, String.valueOf(placementId), String.valueOf(sourceType)), "");
        if (StringUtils.isNotBlank(bidSumRequestSum) && StringUtils.split(bidSumRequestSum, "_").length == 2) {
            return Long.parseLong(StringUtils.split(bidSumRequestSum, "_")[1]);
        } else {
            return 0;
        }
    }

    public long getRevenueSum(String date, long placementId, int sourceType) {
        String revenueSumRequestSum = placementIdSourceTypeRevenueSum.getOrDefault(Tuples.of(date, String.valueOf(placementId), String.valueOf(sourceType)), "");
        if (StringUtils.isNotBlank(revenueSumRequestSum)) {
            return Long.parseLong(StringUtils.split(revenueSumRequestSum, "_")[0]);
        } else {
            return 0;
        }
    }
}