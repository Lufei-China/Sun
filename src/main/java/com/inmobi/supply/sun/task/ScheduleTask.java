package com.inmobi.supply.sun.task;

import java.time.Duration;

import javax.annotation.PostConstruct;

import com.google.common.base.Charsets;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.GeTuiEnrichConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.constant.RequestHeader;
import com.inmobi.supply.sun.model.getui.GeTuiAuthResponse;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnricherGeTui;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Service
public class ScheduleTask {

    @Autowired
    private EnricherGeTui enricherGeTui;
    @Autowired
    private DynamicConfigService dynamicConfigService;
    @Autowired
    private HttpClient httpClient;

    @PostConstruct
    public void refreshGeTuiAuthToken() {
        log.info("ScheduleTask refreshGeTuiAuthToken exec ...");
        Flux.interval(Duration.ofMillis(1),Duration.ofSeconds(300))
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("ScheduleTask refreshGeTuiAuthToken authToken");
                    return authToken();
                })
                .subscribe(p -> log.info("ScheduleTask refreshGeTuiAuthToken exec finish"));
    }

    private Mono<Boolean> authToken() {
        String geTuiConfigStr = dynamicConfigService.getConfig("application").getProperty("enrich.getui", "");
        GeTuiEnrichConfig geTuiConfig = ObjectMapperUtils.fromJSON(geTuiConfigStr, GeTuiEnrichConfig.class);

        long ts = System.currentTimeMillis();
        String md5OfTS = DigestUtils.md5Hex(Long.toString(ts));
        String sign1 = DigestUtils.md5Hex(geTuiConfig.getUserCode() + md5OfTS);
        String sign2 = DigestUtils.md5Hex(geTuiConfig.getUserCode() + geTuiConfig.getAuthCode() + md5OfTS);

        StringBuilder sb = new StringBuilder(geTuiConfig.getAuthUrl())
                .append("?user_code=").append(geTuiConfig.getUserCode())
                .append("&sign1=").append(sign1)
                .append("&sign2=").append(sign2)
                .append("&timestamp=").append(ts);

        return httpClient.keepAlive(true)
                .headers(h -> h.set("Accept", RequestHeader.GETUI_ACCEPT_HEADER))
                .get()
                .uri(sb.toString())
                .responseContent()
                .aggregate()
                .asByteArray()
                .timeout(Duration.ofSeconds(2))
                .flatMap(res -> {
                    String authRes = new String(res, Charsets.UTF_8);
                    GeTuiAuthResponse authResponse = ObjectMapperUtils.fromJSON(authRes, GeTuiAuthResponse.class);
                    log.info("ScheduleTask refreshGeTuiAuthToken authResponse: {}", authResponse);
                    enricherGeTui.setAuthResponse(authResponse);
                    return Mono.just(Boolean.TRUE);
                }).onErrorResume(throwable -> {
                    log.error("ScheduleTask refreshGeTuiAuthToken get auth token error:", throwable.getMessage());
                    return Mono.just(Boolean.FALSE);
                });
    }
}