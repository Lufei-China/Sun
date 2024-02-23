package com.inmobi.supply.sun.service.preProc.enrichment.module;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.inmobi.pb.api.OsType;
import com.alibaba.csp.sentinel.adapter.reactor.SentinelReactorTransformer;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiter;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.sun.config.GeTuiEnrichConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RequestHeader;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.getui.GeTuiAuthResponse;
import com.inmobi.supply.sun.model.getui.GeTuiResponse;
import com.inmobi.supply.sun.model.getui.GeTuiTaskListRequest;
import com.inmobi.supply.sun.model.redis.GeTuiProjectTask;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.file.UserGroupRepo;
import com.inmobi.supply.sun.repo.redis.service.GeTuiProjectTaskRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.GETUI_RATE_LIMITER;
import static com.inmobi.supply.sun.constant.RedisKey.GETUI_REDIS_KEY_PREFIX;
import static com.inmobi.supply.sun.constant.RedisKey.LOCAL_USER_GROUP_REDIS_KEY_PREFIX;

/**
 * 各推/本地人群包构建
 *
 * @author zeki.zhao
 */
@Slf4j
@Service
@SuppressWarnings("unchecked")
public class EnricherGeTui implements ModuleWorker<Integer> {
    private static final String ZERO = "00000000-0000-0000-0000-000000000000";
    private static final String IDFA_ZERO_MD5 = "9F89C84A559F573636A47FF8DAED0D33";
    @Getter
    @Setter
    private GeTuiAuthResponse authResponse = new GeTuiAuthResponse();

    @Autowired
    private GeTuiProjectTaskRepo geTuiProjectTaskRepo;

    /*@Autowired
    private GeTuiProjectTaskOssRepo geTuiProjectTaskRepo;*/

    @Autowired
    @Qualifier("reactiveRedisTemplate2")
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private HttpClient httpClient;
    @Autowired
    private UserGroupRepo userGroupRepo;

    private static Random random = new Random();

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        LogUtils.logInfo(runTimeContext.getRequestId(), "EnricherGeTui");
        PerfUtils.perf("sun.getui.request", Tag.of("type", "all"));
        try {
            // 百度流量为了降低latency,去掉个推（暂时也用不到个推）
            if (runTimeContext.getPlacementModel().getAccountId().equals(DynamicStringConfig.BAIDU_ACCOUNT.get())) {
                return Mono.just(0);
            }
            GeTuiEnrichConfig geTuiConfig = DynamicJsonConfig.ENRICH_GETUI.get();
            // enrich getui is enable
            if (!geTuiConfig.isEnableGeTui()) {
                return Mono.just(0);
            }
            PerfUtils.perf("sun.getui.request", Tag.of("type", "prepare"));
            // 获取本次个推传参
            OsType osType = runTimeContext.getDevice().getOsType();
            Device.IdMapping deviceId = runTimeContext.getDevice().getId();
            GeTuiTaskListRequest taskListRequest = new GeTuiTaskListRequest();
            LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} osType {} deviceId {}", runTimeContext.getPlacementId(), osType, deviceId);
            // 安卓支持oaid优先，imei其次的需求

            if (OsType.IOS.equals(osType)) {
                if (validIdfa(deviceId.getIdfa())) {
                    taskListRequest.setIdfaMD5(DigestUtils.md5Hex(deviceId.getIdfa()));
                    taskListRequest.setType("idfa");
                } else if (validIdfaMd5(deviceId.getIdfaMd5())) {
                    taskListRequest.setIdfaMD5(deviceId.getIdfaMd5());
                    taskListRequest.setType("idfa");
                } else {
                    LogUtils.logWarn(runTimeContext.getRequestId(), "geTui siteId {} Ios drop idfa {} idfaMd5", runTimeContext.getPlacementId(), deviceId.getIdfa(), deviceId.getIdfaMd5());
                    return Mono.just(0);
                }
            } else if (OsType.ANDROID.equals(osType)) {
                if (validIdfa(deviceId.getOaid())) {
                    taskListRequest.setType("oaid");
                    taskListRequest.setOaidMd5(DigestUtils.md5Hex(deviceId.getOaid()));
                } else if (validIdfa(deviceId.getOaidMd5())) {
                    taskListRequest.setType("oaid");
                    taskListRequest.setOaidMd5(deviceId.getOaidMd5());
                } else if (validIdfa(deviceId.getImeiMd5())) {
                    taskListRequest.setType("imei");
                    taskListRequest.setImeiMD5(deviceId.getImeiMd5());
                } else {
                    LogUtils.logWarn(runTimeContext.getRequestId(), "geTui siteId {} Android drop imeiMd5 {} oaidMd5 {} oaid {}", runTimeContext.getPlacementId(), deviceId.getImeiMd5(), deviceId.getOaidMd5(), deviceId.getOaid());
                    return Mono.just(0);
                }
            } else {
                return Mono.just(0);
            }
            taskListRequest.setOs(osType.name());
            PerfUtils.perf("sun.getui.request", Tag.of("type", "validParam"));
            //redis cache
            String redisKey = getDeviceRedisKey(taskListRequest.getfirstMd5());
            if (StringUtils.isBlank(redisKey)) {
                LogUtils.logWarn(runTimeContext.getRequestId(), "geTui redisKey is empty!");
                return Mono.just(0);
            }
            LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} requestIdInfo {} redisKey {}", runTimeContext.getPlacementId(), taskListRequest, redisKey);
            StringBuilder caidBuilder = new StringBuilder();
            //本地人群包
            if (userGroupRepo.getBloomFilterMap() != null && CollectionUtils.isNotEmpty(userGroupRepo.getBloomFilterMap().values())) {
                userGroupRepo.getBloomFilterMap().values().forEach(tuple -> {
                    if (tuple.getT3() == osType) {
                        switch (osType) {
                            case ANDROID:
                                if (tuple.getT1().mightContain(StringUtils.lowerCase(deviceId.getImeiMd5()))) {
                                    caidBuilder.append(tuple.getT2()).append(",");
                                    PerfUtils.perf("sun.userGroup.enrich", Tag.of("type", tuple.getT2()));
                                }
                                break;
                            case IOS:
                                if (tuple.getT1().mightContain(StringUtils.upperCase(deviceId.getIdfa()))) {
                                    caidBuilder.append(tuple.getT2()).append(",");
                                    PerfUtils.perf("sun.userGroup.enrich", Tag.of("type", tuple.getT2()));
                                }
                                break;
                        }
                    }
                });
            }

            // 人群包命中后，先set一遍，防止后续错误发生没有赋值
            if (StringUtils.isNotBlank(caidBuilder)) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} userGroup hit caidBuilder={}", runTimeContext.getPlacementId(), caidBuilder);
                runTimeContext.setCsids(caidBuilder.substring(0, caidBuilder.length() - 1));
            }

            return Mono.just(geTuiConfig.isEnableCache()).flatMap(enabled -> {
                //开启缓存的话
                if (enabled) {
                    return reactiveRedisTemplate.opsForValue().get(redisKey).switchIfEmpty(Mono.just("")).flatMap(value -> {
                        // 注意value空指针
                        LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} redisKey {} value {}", runTimeContext.getPlacementId(), redisKey, value);
                        if (StringUtils.isBlank(value)) {
                            PerfUtils.perf("sun.getui.request", Tag.of("type", "cacheNull"));
                        } else if (RuntimeConstant.EMPTY_STR.equalsIgnoreCase(value)) {
                            PerfUtils.perf("sun.getui.request", Tag.of("type", "cacheEmpty"));
                        } else {
                            PerfUtils.perf("sun.getui.request", Tag.of("type", "cacheHit"));
                        }
                        if (StringUtils.isNotBlank(value) && !RuntimeConstant.EMPTY_STR.equalsIgnoreCase(value)) {
                            String csids = getCsids(ObjectMapperUtils.fromJSON(value.trim(), Map.class), false);
                            if (StringUtils.isNotBlank(csids)) {
                                caidBuilder.append(csids);
                                LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} cache hit caidBuilder={}", runTimeContext.getPlacementId(), caidBuilder);
                                // 命中getui缓存后继续赋值
                                runTimeContext.setCsids(caidBuilder.toString());
                                return Mono.just(false);
                            }
                        }
                        return Mono.just(true);
                    }).flatMap(needRequest -> {
                                LogUtils.logInfo(runTimeContext.getRequestId(), "geTui siteId {} prepare status needRequest:{} expired:{}", runTimeContext.getPlacementId(), needRequest, authResponse.isExpired());
                                //没有命中缓存，并且token没有过期
                                if (needRequest && !authResponse.isExpired() && geTuiProjectTaskRepo.needRequest(runTimeContext.getPlacementId())) {
                                    return requestGeTui(runTimeContext, geTuiConfig, osType, taskListRequest, redisKey, caidBuilder);
                                }
                                return Mono.just(0);
                            }
                    );
                } else {//没有开启缓存
                    LogUtils.logInfo(runTimeContext.getRequestId(), "geTui disable cache siteId {} expired:{}", runTimeContext.getPlacementId(), authResponse.isExpired());
                    if (!authResponse.isExpired() && geTuiProjectTaskRepo.needRequest(runTimeContext.getPlacementId())) {
                        return requestGeTui(runTimeContext, geTuiConfig, osType, taskListRequest, redisKey, caidBuilder);
                    }
                }
                return Mono.just(0);
            });
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private boolean validIdfaMd5(String idfaMd5) {
        return StringUtils.isNotBlank(idfaMd5) && !IDFA_ZERO_MD5.equals(idfaMd5);
    }

    private boolean validIdfa(String idfa) {
        return StringUtils.isNotBlank(idfa) && !ZERO.equals(idfa);
    }

    private Mono<Integer> requestGeTui(RunTimeContext runTimeContext, GeTuiEnrichConfig geTuiConfig, OsType osType,
                                       GeTuiTaskListRequest taskListRequest, String deviceRedisKey, StringBuilder csidBuilder) {
        Map<String, List<String>> projectTaskList = getProjectTaskList(runTimeContext.getPlacementId());

        taskListRequest.setUserCode(geTuiConfig.getUserCode());
        taskListRequest.setProjectTaskList(projectTaskList);
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", RequestHeader.GETUI_ACCEPT_HEADER);
        headers.put("routeId", taskListRequest.getfirstMd5());
        headers.put("Authorization", "Bearer " + authResponse.getAccess_token());
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        PerfUtils.perf("sun.getui.request", Tag.of("type", "request"));
        LogUtils.logInfo(runTimeContext.getRequestId(), "siteId {} request geTui headers:{} body:{}", runTimeContext.getPlacementId(), headers, ObjectMapperUtils.toJSON(taskListRequest));
        return httpClient
                .headers(h -> headers.entrySet().iterator().forEachRemaining(header -> h.set(header.getKey(), header.getValue())))
                .keepAlive(true)
                .post()
                .uri(geTuiConfig.getTaskUrl())
                .send(ByteBufFlux.fromInbound(Mono.just(ObjectMapperUtils.toJSON(taskListRequest).getBytes())))
                .responseContent()
                .aggregate()
                .asByteArray()
                .timeout(Duration.ofMillis(geTuiConfig.getTimeout()))
                .transform(new SentinelReactorTransformer<>("geTuiEndPoint"))
                .flatMap(res -> {
                    PerfUtils.perf("sun.getui.request", Tag.of("type", "success"));
                    String taskListRes = new String(res, Charsets.UTF_8);
                    LogUtils.logInfo(runTimeContext.getRequestId(), "siteId {} geTui response {}", runTimeContext.getPlacementId(), taskListRes);
                    if (StringUtils.isNotBlank(taskListRes)) {
                        GeTuiResponse geTuiResponse = ObjectMapperUtils.fromJSON(taskListRes, GeTuiResponse.class);
                        if (RuntimeConstant.GETUI_AUTH_EXPIRED.equalsIgnoreCase(geTuiResponse.getCode())) {
                            authResponse.forceTokenExpiry();
                            return Mono.just(0);
                        }
                        Map<String, List<String>> data = geTuiResponse.getData();
                        long timeout, randSec;
                        String cacheVal;
                        if (data != null && data.size() != 0) {
                            PerfUtils.perf("sun.getui.request", Tag.of("type", "requestHit"));
                            LogUtils.logInfo(runTimeContext.getRequestId(), "siteId {} geTui response type {} hit {}", runTimeContext.getPlacementId(), taskListRequest.getType(), geTuiResponse);
                            String csids = getCsids(data, true);
                            if (StringUtils.isNotBlank(csids)) {
                                csidBuilder.append(csids);
                                LogUtils.logInfo(runTimeContext.getRequestId(), "siteId {} geTui realRequest hit caidBuilder={}", runTimeContext.getPlacementId(), csidBuilder);
                                runTimeContext.setCsids(csidBuilder.toString());
                            } else {
                                //命中了个推返回，但是没有csid，需要警告
                                LogUtils.logWarn(runTimeContext.getRequestId(), "siteId {} geTui response type {} hit {} but no csid found!", runTimeContext.getPlacementId(), taskListRequest.getType(), geTuiResponse);
                            }
                            timeout = geTuiConfig.getCacheForValSec();
                            cacheVal = ObjectMapperUtils.toJSON(data);
                        } else {
                            timeout = geTuiConfig.getCacheForNullSec();
                            cacheVal = RuntimeConstant.EMPTY_STR;
                        }
                        randSec = random.nextInt((int) timeout / 10);
                        if (geTuiConfig.isEnableCache()) {
                            return reactiveRedisTemplate.opsForValue().set(deviceRedisKey, cacheVal, Duration.ofSeconds(timeout + randSec))
                                    .then(Mono.just(0));
                        }
                    }
                    return Mono.just(0);
                })
                .onErrorResume(throwable -> {
                    LogUtils.logInfo(runTimeContext.getRequestId(), "siteId {} geTui error {}", runTimeContext.getPlacementId(), throwable);
                    PerfUtils.perf("sun.getui.request", Tag.of("type", "error"));
                    return Mono.just(0);
                }).elapsed().flatMap(tuple -> {
                    PerfUtils.timer("sun.getui.latency",tuple.getT1());
                    return Mono.just(tuple.getT2());
                });
    }


    private String getDeviceRedisKey(String md5) {
        return GETUI_REDIS_KEY_PREFIX.of(md5);
    }


    private Map<String, List<String>> getProjectTaskList(long placementId) {
        /*Map<String, List<String>> data = new HashMap<>();
        if (MapUtils.isNotEmpty(geTuiProjectTaskRepo.getGeTuiProjectTaskMap())) {
            geTuiProjectTaskRepo.getGeTuiProjectTaskMap()
                    .values()
                    .forEach(pt -> {
                        data.computeIfAbsent(pt.getProjectId(), k -> new ArrayList<>());
                        if (!data.get(pt.getProjectId()).contains(pt.getTaskId())) {
                            data.get(pt.getProjectId()).add(pt.getTaskId());
                        }
                    });
        }
        if (data.keySet().size() < 2 && MapUtils.isNotEmpty(geTuiProjectTaskRepo.getGeTuiRtbProjectTaskMap())) {
            int currentSize = 0;
            for (List<String> value : data.values()) {
                currentSize += value.size();
            }
            GeTuiEnrichConfig geTuiConfig = DynamicJsonConfig.ENRICH_GETUI.get();
            geTuiProjectTaskRepo.getGeTuiRtbProjectTaskMap()
                    .values()
                    .stream()
                    .limit(geTuiConfig.getTaskLimit() - currentSize)
                    .collect(Collectors.toList())
                    .forEach(pt -> {
                        data.computeIfAbsent(pt.getProjectId(), k -> new ArrayList<>());
                        if (!data.get(pt.getProjectId()).contains(pt.getTaskId())) {
                            data.get(pt.getProjectId()).add(pt.getTaskId());
                        }
                    });
        }*/
        Map<String, List<GeTuiProjectTask>> geTuiPlacementCache = geTuiProjectTaskRepo.getGeTuiPlacementCache();
        Map<String, List<String>> data = new HashMap<>();
        List<GeTuiProjectTask> list = Lists.newArrayList();
        if (geTuiPlacementCache.get(GeTuiProjectTaskRepo.ALL) != null) {
            list.addAll(geTuiPlacementCache.get(GeTuiProjectTaskRepo.ALL));
        }
        if (geTuiPlacementCache.get(String.valueOf(placementId)) != null) {
            list.addAll(geTuiPlacementCache.get(String.valueOf(placementId)));
        }
        list.forEach(p -> {
            if (data.get(p.getProjectId()) == null) {
                data.put(p.getProjectId(), Lists.newArrayList(p.getTaskId()));
            } else {
                data.get(p.getProjectId()).add(p.getTaskId());
            }
        });
        return data;
    }

    private String getCsids(Map<String, List<String>> data, boolean realTime) {
        try {
            StringBuilder sb = new StringBuilder();
            data.values()
                    .forEach(list -> list
                            .forEach(v -> {
                                final GeTuiProjectTask task = geTuiProjectTaskRepo.getTask(v);
                                if (null != task) {
                                    PerfUtils.perf("sun.getui.response", Tag.of("csid", task.getCsid()), Tag.of("source", String.valueOf(realTime)));
                                    sb.append(task.getCsid()).append(",");
                                }
                            }));
            if (StringUtils.isBlank(sb)) {
                return null;
            }
            return sb.substring(0, sb.length() - 1);
        } catch (Exception e) {
            log.error("Getui get csid error, data: {}", data, e);
            return null;
        }
    }
}