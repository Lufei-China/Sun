package com.inmobi.supply.sun.repo.redis.service.supplySelection;

import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.supplySelection.SupplySelectionRedisImp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.inmobi.supply.sun.utils.WorkerUtils.nDaysBefore;

/**
 * SS sum value
 * @author remi.li
 */
@Slf4j
//@Service
public class SupplySelectionSumRepo implements BaseRepo<Object> {
    private volatile Map<String, String> marketBidSumCache = new HashMap<>(); // <date, marketBidSum>
    private volatile Map<String, String> marketRevenueSumCache = new HashMap<>(); // <date, marketRevenueSum>
    private volatile Map<String, String> marketRequestSumCache = new HashMap<>(); // <date, marketRequestSum>

    @Autowired
    private SupplySelectionRedisImp supplySelectionRedisImp;

    @Override
    public Flux<Object> loadRepo() {
        Map<String, String> newMarketBidSumCache = new HashMap<>();
        Map<String, String> newMarketRevenueSumCache = new HashMap<>();
        Map<String, String> newMarketRequestSumCache = new HashMap<>();
        //fetch N days before date
        return Flux.fromIterable(nDaysBefore(7)).flatMap(new Function<String, Flux<String>>() {
            @Override
            public Flux<String> apply(String date) {
                Mono<String> marketBidSumRequestSumMono = supplySelectionRedisImp.getMarketBidSumByDate(date);
                Mono<String> marketRevenueSumMono = supplySelectionRedisImp.getMarketRevenueSumByDate(date);
                return marketBidSumRequestSumMono.zipWith(marketRevenueSumMono).flatMap(new Function<Tuple2<String, String>, Mono<String>>() {
                    @Override
                    public Mono<String> apply(Tuple2<String, String> sumValue) {
                        if (StringUtils.isNotBlank(sumValue.getT1())) {
                            if (StringUtils.split(sumValue.getT1(),"_").length == 2) {
                                newMarketBidSumCache.put(date, StringUtils.split(sumValue.getT1(),"_")[0]);
                                newMarketRequestSumCache.put(date, StringUtils.split(sumValue.getT1(),"_")[1]);
                            }
                        }
                        if (StringUtils.isNotBlank(sumValue.getT2())) {
                            newMarketRevenueSumCache.put(date, sumValue.getT2());
                        }
                        return Mono.just("");
                    }
                }).thenMany(Flux.just("").flatMap(integer -> {
                    marketBidSumCache = newMarketBidSumCache;
                    marketRevenueSumCache = newMarketRevenueSumCache;
                    marketRequestSumCache = newMarketRequestSumCache;
                    return Flux.just("");
                }));
            }
        });
    }

    public long getMarketBidSum(String date) {
        String marketBidSum = marketBidSumCache.getOrDefault(date, "");
        if (StringUtils.isNotBlank(marketBidSum)) {
            return Long.parseLong(marketBidSum);
        } else {
            return 0;
        }
    }

    public long getMarketRequestSum(String date) {
        String marketBidSum = marketRequestSumCache.getOrDefault(date, "");
        if (StringUtils.isNotBlank(marketBidSum)) {
            return Long.parseLong(marketBidSum);
        } else {
            return 0;
        }
    }

    public long getMarketRevenueSum(String date) {
        String marketBidSum = marketRevenueSumCache.getOrDefault(date, "");
        if (StringUtils.isNotBlank(marketBidSum)) {
            return Long.parseLong(marketBidSum);
        } else {
            return 0;
        }
    }
}
