package com.inmobi.supply.sun.task;

import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Stream;

@Slf4j
@Service("IntervalIpcClientMonitor")
public class initIntervalIpcClientMonitorTask {
    @Autowired
    private PrometheusOverlord prometheusOverlord;

    private long delaySeconds = 30L;
    private long intervalSeconds = 60L;

    @PostConstruct
    public void initIntervalIpcClientMonitorTask() {
        Flux.interval(Duration.ofSeconds(delaySeconds), Duration.ofSeconds(intervalSeconds))
                .parallel().runOn(Schedulers.elastic()).flatMap(x -> {
            log.info("IntervalIpcClientMonitor begin...");
            String sunRRfilePath = System.getProperty("sunRR.file.path", "");
            String wnfilePath = System.getProperty("winnoticeRR.file.path", "");

            if (StringUtils.isNotBlank(sunRRfilePath)) {
                try (Stream<Path> files = Files.list(Paths.get(sunRRfilePath))) {
                    long count = files.count();
                    prometheusOverlord.getSunRRIpcClientFileCount().set(count);
                } catch (IOException e) {
                    log.error("initIntervalIpcClientMonitorTask error,{}", e.getMessage());
                }
            } else {
                prometheusOverlord.getSunRRIpcClientFileCount().set(0L);
            }

            if (StringUtils.isNotBlank(wnfilePath)) {
                try (Stream<Path> files = Files.list(Paths.get(wnfilePath))) {
                    long count = files.count();
                    prometheusOverlord.getWnIpcClientFileCount().set(count);
                } catch (IOException e) {
                    log.error("initIntervalIpcClientMonitorTask error,{}", e.getMessage());
                }
            } else {
                prometheusOverlord.getWnIpcClientFileCount().set(0L);
            }
            return Mono.empty();
        }).subscribe();
    }
}