package com.inmobi.supply.sun.service.mainProc;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.alibaba.csp.sentinel.adapter.reactor.SentinelReactorTransformer;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.Init2Service;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.DEBUG_PLACEMENT_ID;
import static com.inmobi.supply.sun.constant.RuntimeConstant.EMPTY_BODY;

/*
 * entrance of sun system
 * @author remi.li
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class SunAdsManager {
    @Autowired
    PrometheusOverlord prometheusOverlord;
    @Autowired
    private Init2Service init2Service;

    /*@Autowired
    @Qualifier("getScheduler")
    private Scheduler scheduler;*/

    @NonNull
    public Mono<ServerResponse> begin(ServerRequest serverRequest) {
        return serverRequest
                .bodyToMono(byte[].class)
                .defaultIfEmpty(EMPTY_BODY)
                .flatMap(input -> {
                    log.info("serverRequest body length:{}", input.length);
                    return Mono.just(input);
                })
                .transform(new SentinelReactorTransformer<>("sunEntrance"))
                .transform(init2Service.getStartModule().transform())
                .contextWrite(context -> {
                    PerfUtils.perf(PerfConstant.REQUEST_TOTAL_COUNT);
                    return init2Service.initContext((Context) context, serverRequest);
                })
                .elapsed()
                .flatMap((Function<Tuple2<Long, AsyncServletResponse>, Mono<AsyncServletResponse>>) tuple2 -> {
                    //do latency metrics
                    if (DEBUG_PLACEMENT_ID.get().contains(Optional.ofNullable(tuple2.getT2().getPlacementId()).orElse(0L))) {
                        PerfUtils.timer("placementId.latency", tuple2.getT1());
                    }
                    if (tuple2.getT2().getTermCode() == 0) {
                        PerfUtils.timer("valid.sun.latency", tuple2.getT1());
                    }
                    if (tuple2.getT2().isBaidu()) {
                        log.info("baiduLatency {} {} str {}", tuple2.getT2().getAdName(), tuple2.getT2().getPlacementId(), "valid.sun.latency" + String.valueOf(tuple2.getT2().getAdName()));
                        if (tuple2.getT2().getAdName().equals("1")) {
                            PerfUtils.timer("feed.sun.latency", tuple2.getT1());
                        } else {
                            PerfUtils.timer("nofeed.sun.latency", tuple2.getT1());
                        }
                    }
                    if (StringUtils.isNotBlank(tuple2.getT2().getChannel())) {
                        PerfUtils.timer("sun.exchange.whole.latency", tuple2.getT1(), Tag.of("channel", tuple2.getT2().getChannel()));
                        LogUtils.logInfo(tuple2.getT2().getRequestId(), "exchange {} fill ad cost whole {}ms", tuple2.getT2().getChannel(), tuple2.getT1());
                    }
                    LogUtils.logInfo(tuple2.getT2().getRequestId(), "last log cost {}ms", tuple2.getT1());
                    PerfUtils.timer("sun.latency", tuple2.getT1());
                    return Mono.just(tuple2.getT2());
                })
                .flatMap((Function<AsyncServletResponse, Mono<ServerResponse>>) asr -> {
                    //build final response
                    ServerResponse.BodyBuilder bodyBuilder = ServerResponse.status(Optional.ofNullable(asr.getStatus()).orElse(HttpStatus.BAD_REQUEST));
                    for (Map.Entry<String, String> header : asr.getHeaders().entrySet()) {
                        bodyBuilder.header(header.getKey(), header.getValue());
                    }
                    if (asr.getContentType() != null) {
                        bodyBuilder.contentType(asr.getContentType());
                    } else {
                        bodyBuilder.contentType(serverRequest.headers().contentType().orElse(MediaType.APPLICATION_JSON));
                    }
                    if (ArrayUtils.isNotEmpty(asr.getResponseBody())) {
                        bodyBuilder.contentLength(asr.getResponseBody().length);
                        return bodyBuilder.bodyValue(asr.getResponseBody());
                    } else {
                        return bodyBuilder.bodyValue(new byte[0]);
                    }
                })
                .name("sun.main.process.latency")
                .metrics()
                .take(Duration.ofMillis(600));
    }
}