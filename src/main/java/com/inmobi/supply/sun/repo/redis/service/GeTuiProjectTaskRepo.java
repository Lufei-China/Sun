package com.inmobi.supply.sun.repo.redis.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.GeTuiEnrichConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.constant.RedisKey;
import com.inmobi.supply.sun.model.redis.GeTuiProjectTask;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GeTuiProjectTaskRepo implements BaseRepo<Integer> {
    @Getter
    private volatile Map<String, GeTuiProjectTask> geTuiProjectTaskMap = new HashMap<>();
    @Getter
    private volatile Map<String, GeTuiProjectTask> geTuiRtbProjectTaskMap = new HashMap<>();
    @Getter
    private volatile Map<String, List<GeTuiProjectTask>> geTuiPlacementCache = Maps.newHashMap();

    @Autowired
    @Qualifier("reactiveRedisTemplate2")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private DynamicConfigService dynamicConfigService;

    public static final String ALL = "all";

    @Override
    public Mono<Integer> loadRepo() {
        log.info("enter GeTui project task id load");

        String geTuiConfigStr = dynamicConfigService.getConfig("application").getProperty("enrich.getui", "");

        GeTuiEnrichConfig config;
        try {
            config = ObjectMapperUtils.fromJSON(geTuiConfigStr, GeTuiEnrichConfig.class);
        } catch (Exception e) {
            log.error("GeTuiProjectTaskOssRepo error", e);
            return Mono.empty();
        }

        Map<String, List<GeTuiProjectTask>> newCache = Maps.newHashMap();

        return reactiveRedisTemplate.opsForValue()
                .get(RedisKey.GETUI_DEMAND_PROJECT_TASK_INFO.getPrefix())
                .defaultIfEmpty("")
                .flatMap(demandJsonValue -> {
                    if (StringUtils.isNotBlank(demandJsonValue)) {
                        List<GeTuiProjectTask> geTuiProjectTasks = ObjectMapperUtils.fromJSONArray(demandJsonValue, GeTuiProjectTask.class);
                        log.info("load demand getui {}", geTuiProjectTasks);
                        if (CollectionUtils.isNotEmpty(geTuiProjectTasks)) {
                            geTuiProjectTaskMap = geTuiProjectTasks.stream().collect(Collectors.toMap(GeTuiProjectTask::getTaskId, Function.identity()));
                            fillGetuiCache(newCache, geTuiProjectTasks);
                        }
                    }
                    return Mono.just(0);
                })
                .onErrorResume(throwable -> {
                    LogUtils.logError("task", "load demand getui repo  error,{}", throwable);
                    return Mono.just(0);
                })
                .then(
                        reactiveRedisTemplate.opsForValue()
                                .get(RedisKey.GETUI_RTB_PROJECT_TASK_INFO.getPrefix())
                                .defaultIfEmpty("")
                                .flatMap(rtbdJsonValue -> {
                                    if (StringUtils.isNotBlank(rtbdJsonValue)) {
                                        List<GeTuiProjectTask> geTuiProjectTasks = ObjectMapperUtils.fromJSONArray(rtbdJsonValue, GeTuiProjectTask.class);
                                        log.info("load rtb getui {}", geTuiProjectTasks);
                                        geTuiRtbProjectTaskMap = geTuiProjectTasks.stream().collect(Collectors.toMap(GeTuiProjectTask::getTaskId, Function.identity()));
                                        fillGetuiCache(newCache, geTuiProjectTasks);
                                    }
                                    return Mono.just(0);
                                })
                ).onErrorResume(throwable -> {
                    LogUtils.logError("task", "load exchange getui repo  error,{}", throwable);
                    return Mono.just(0);
                }).then(
                    Mono.just(0).flatMap(
                        t -> {
                            this.geTuiPlacementCache = newCache;
                            LogUtils.logInfo("task", "load getui cache {}", ObjectMapperUtils.toJSON(newCache));
                            return Mono.just(0);
                        }
                    )
                );
    }

    private void fillGetuiCache(Map<String, List<GeTuiProjectTask>> newCache, List<GeTuiProjectTask> geTuiProjectTasks) {
        geTuiProjectTasks.stream().forEachOrdered(p -> {
            List<Long> placementIds = p.getPlacementIds();
            if (CollectionUtils.isEmpty(placementIds)) {
                if (newCache.get(ALL) != null) {
                    newCache.get(ALL).add(p);
                } else {
                    newCache.put(ALL, Lists.newArrayList(p));
                }
            } else {
                placementIds.stream().forEachOrdered(l -> {
                    String pid = String.valueOf(l);
                    if (newCache.get(pid) != null) {
                        newCache.get(pid).add(p);
                    } else {
                        newCache.put(pid, Lists.newArrayList(p));
                    }
                });
            }

        });
    }

    public GeTuiProjectTask getTask(String taskId) {
        return Optional.ofNullable(geTuiProjectTaskMap)
                .orElse(new HashMap<>())
                .getOrDefault(taskId, Optional.ofNullable(geTuiRtbProjectTaskMap).orElse(new HashMap<>())
                        .getOrDefault(taskId, null));
    }

    /**
     * 判断该广告位是否有必要请求个推
     * @param placementId
     * @return
     */
    public boolean needRequest(long placementId){
        if (CollectionUtils.isNotEmpty(geTuiPlacementCache.get(ALL))){
            return true;
        } else if(CollectionUtils.isNotEmpty(geTuiPlacementCache.get(String.valueOf(placementId)))){
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return Optional.ofNullable(geTuiProjectTaskMap).orElse(new HashMap<>()).size();
    }
}