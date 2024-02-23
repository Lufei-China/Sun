package com.inmobi.supply.sun.repo.redis.service;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;
import com.google.gson.reflect.TypeToken;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.inmobi.supply.sun.constant.RedisKey.*;

/**
 * placement信息本地缓存+redis Repo
 *
 * @author remi.li
 */

@Service
@Slf4j
public class NoviceRepo implements BaseRepo<Integer> {
    private static final long NOVICE_INTERVAL_SECONDS = 10 * 60L;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private volatile Set<Long> noviceNetRev0Cache = new HashSet();

    private volatile Set<Long> noviceNewerCache = new HashSet();

    private volatile Set<Long> reqBpCache = new HashSet();

    private volatile Set<String> cityBpCache = new HashSet();

    private volatile Set<Long> alCache = new HashSet();

    private volatile Set<Long> hourCache = new HashSet();

    private volatile Map<String, BigDecimal> allPlacementRcpm = new HashMap();

    private volatile Set<String> placementRequestDay = new HashSet<>();

    @Override
    public Flux<Integer> loadRepo() {
        LogUtils.logTask("start load placement novice repo!");
        Set<Long> newPlaceCache = new HashSet<>();
        return reactiveRedisTemplate.opsForValue()
                .get(NOVICE_NETREV_LIST.getPrefix())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newPlaceCache.addAll(new Gson().fromJson(str, new TypeToken<Set<Long>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    LogUtils.logTask("load zero netRev placementRepo {}", newPlaceCache);
                    noviceNetRev0Cache = newPlaceCache;
                    return Flux.just(0);
                })).flatMap(str2 -> {
                    return loadReqBp();
                }).flatMap(bpStr -> {
                    return loadRepoNewer();
                }).flatMap(bpStr -> {
                    return loadCityBp();
                }).flatMap(bpStr -> {
                    return loadAl();
                }).flatMap(bpStr -> {
                    return loadAllPlacementRcpm();
                }).flatMap(bpStr -> {
                    return loadRequestDay();
                }).flatMap(bpStr -> {
                    return loadHour();
                })
                .onErrorResume(exception -> {
//                    log.error("NoviceRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });

    }

    private Flux<Integer> loadRepoNewer() {
        Set<Long> newPlaceCache = new HashSet<>();
        return reactiveRedisTemplate.opsForValue()
                .get(NOVICE_NEWER_LIST.getPrefix())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newPlaceCache.addAll(new Gson().fromJson(str, new TypeToken<Set<Long>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    noviceNewerCache = newPlaceCache;
                    return Flux.just(0);
                })).onErrorResume(exception -> {
//                    log.error("NoviceRepo loadRepo error:" + exception.getMessage());
                    return Flux.just(0);
                });

    }


    private Flux<Integer> loadReqBp() {
        Set<Long> newPlaceCache = new HashSet<>();
        return reactiveRedisTemplate.opsForSet()
                .members(RedisKey.NOVICE_NOVICE_BP_REQ.getPrefix())
                .switchIfEmpty(Flux.error(new Exception("no loadReqBp elements")))
                .flatMap(str -> {
                    newPlaceCache.add(Long.valueOf(str));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    reqBpCache = newPlaceCache;
                    LogUtils.logTask("load BP placementRepo {}", newPlaceCache);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load BP placementRepo error {}", exception);
                    return Flux.just(0);
                });

    }

    private Flux<Integer> loadCityBp() {
        Set<String> newPlaceCache = new HashSet<>();

        return reactiveRedisTemplate.opsForSet()
                .members(RedisKey.NOVICE_NOVICE_BP.getPrefix())
                .switchIfEmpty(Flux.error(new Exception("no loadReqBp elements")))
                .flatMap(str -> {
                    newPlaceCache.add(str);
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    cityBpCache = newPlaceCache;
                    LogUtils.logTask("load BP city placementRepo {}", newPlaceCache);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load BP city placementRepo error {}", exception);
                    return Flux.just(0);
                });

    }

    private Flux<Integer> loadAl() {
        Set<Long> newPlaceCache = new HashSet<>();

        return reactiveRedisTemplate.opsForValue()
                .get(generateKey())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newPlaceCache.addAll(new Gson().fromJson("[" + str.replace(" ", "") + "]", new TypeToken<Set<Long>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    alCache = newPlaceCache;
                    LogUtils.logTask("load alCache placementRepo {}", newPlaceCache);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load alCache placementRepo error {}", exception);
                    return Flux.just(0);
                });

    }

    private Flux<Integer> loadHour() {
        Set<Long> newPlaceCache = new HashSet<>();

        return reactiveRedisTemplate.opsForValue()
                .get(NOVICE_NOVICE_HOUR.getPrefix())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newPlaceCache.addAll(new Gson().fromJson(str, new TypeToken<Set<Long>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    hourCache = newPlaceCache;
                    LogUtils.logTask("load hour white placementRepo {}", newPlaceCache);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load hour white placementRepo error {}", exception);
                    return Flux.just(0);
                });

    }

    private Flux<Integer> loadAllPlacementRcpm() {
        Map<String, BigDecimal> newAllPlacementRcpm = new HashMap();
        return reactiveRedisTemplate.opsForValue()
                .get(ALL_PLACEMENT_RCPM.getPrefix())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newAllPlacementRcpm.putAll(new Gson().fromJson(str, new TypeToken<Map<String, BigDecimal>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    // 对比差异
                    HashSet<Object> placementChange = new HashSet<>();
                    for (String s : newAllPlacementRcpm.keySet()) {
                        BigDecimal newValue = newAllPlacementRcpm.get(s);
                        BigDecimal oldValue = allPlacementRcpm.get(s);
                        if (Objects.isNull(oldValue) || newValue.compareTo(oldValue) != 0) {
                            placementChange.add(s);
                        }
                    }
                    LogUtils.logTask("Rcpm Change, placement {}", placementChange);
                    allPlacementRcpm = newAllPlacementRcpm;
                    LogUtils.logTask("load allPlacementRcpm {}", allPlacementRcpm);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load allPlacementRcpm error {}", exception);
                    return Flux.just(0);
                });
    }

    private Flux<Integer> loadRequestDay() {
        Set<String> newPlacementRequestDay = new HashSet<>();
        return reactiveRedisTemplate.opsForValue()
                .get(NOVICE_PLACEMENT_REQUEST_DAY.getPrefix())
                .defaultIfEmpty("")
                .flatMap(str -> {
                    newPlacementRequestDay.addAll(new Gson().fromJson(str, new TypeToken<Set<String>>() {
                    }.getType()));
                    return Mono.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    placementRequestDay = newPlacementRequestDay;
                    LogUtils.logTask("load placementRequestDay {}", placementRequestDay);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    LogUtils.logTask("load placementRequestDay error {}", exception);
                    return Flux.just(0);
                });
    }

    @Override
    public int size() {
        return Optional.ofNullable(noviceNetRev0Cache).orElse(new HashSet<Long>()).size();
    }

    /*
     * 判断是否7天内的netRev是否为0
     * */
    public boolean isNetRev0(Long placementId) {
        if (noviceNetRev0Cache == null) {
            return false;
        }
        return noviceNetRev0Cache.contains(placementId);
    }

    /*
     * 判断是否7天内的新创建的
     * */
    public boolean isNewer(Long placementId) {
        if (noviceNewerCache == null) {
            return false;
        }
        return noviceNewerCache.contains(placementId);
    }

    public boolean isRequestDay(String placementId) {
        if (placementRequestDay == null) {
            return false;
        }
        return placementRequestDay.contains(placementId);
    }

    public boolean isReqBpLow(Long placementId) {
        if (placementId <= 0) {
            return false;
        }
        if (reqBpCache == null) {
            return false;
        }
        return reqBpCache.contains(placementId);
    }

    public boolean isCityLow(Long placementId, String city) {
        if (placementId <= 0 || StringUtils.isBlank(city)) {
            return false;
        }
        if (cityBpCache == null) {
            return false;
        }
        return cityBpCache.contains(String.valueOf(placementId) + "_" + city);
    }

    public boolean isAlLow(Long placementId) {
        if (placementId <= 0) {
            return false;
        }
        if (alCache == null) {
            return false;
        }
        return alCache.contains(placementId);
    }

    public boolean isHourList(Long placementId) {
        if (placementId <= 0) {
            return false;
        }
        if (hourCache == null) {
            return false;
        }
        return hourCache.contains(placementId);
    }

    public boolean isLow(String placementId, Float rCost) {
        log.info("allPlacementRcpm {}", allPlacementRcpm);
        if (allPlacementRcpm.keySet().size() <= 0) {
            return false;
        }
        if (allPlacementRcpm == null) {
            return false;
        }
        return allPlacementRcpm.get(placementId)!=null && allPlacementRcpm.get(placementId).compareTo(BigDecimal.valueOf(rCost)) < 0;
    }

    /**
     * 任务间隔执行秒数
     *
     * @return
     */
    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(NOVICE_INTERVAL_SECONDS);
    }

    String generateKey() {
        // 获取当前时间
        LocalDateTime localDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH");
        return RedisKey.NOVICE_NOVICE_AL.getPrefix() + formatter.format(localDateTime);
    }

    public static void main(String[] args) {
        Set<Long> newPlaceCache = new HashSet<>();
        newPlaceCache.addAll(new Gson().fromJson("", new TypeToken<Set<Long>>() {
        }.getType()));


        System.out.println(JSON.toJSONString(newPlaceCache));
    }
}