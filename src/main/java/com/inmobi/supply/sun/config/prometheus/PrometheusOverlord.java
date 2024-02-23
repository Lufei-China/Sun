package com.inmobi.supply.sun.config.prometheus;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import com.google.common.util.concurrent.AtomicDouble;
import com.inmobi.supply.adapterlibrary.service.CountService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Getter
@Slf4j
public class PrometheusOverlord {
    @Autowired
    private MeterRegistry meterRegistry;
    //private Timer sunTimer;
    private AtomicLong sunRRIpcClientFileCount;
    private AtomicLong wnIpcClientFileCount;
    private AtomicDouble exchangeRcost;
    private AtomicDouble demandRcost;
    //private Timer perfTimer;
    //private Timer placementIdTimer;
    //private Timer saturnTimer;

    @PostConstruct
    public void init() {
        Metrics.globalRegistry
                .config()
                .meterFilter(MeterFilter.denyNameStartsWith("executor"));
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, @NonNull DistributionStatisticConfig config) {
                /*if (id.getName().startsWith("reactor.flow.duration")
                        || id.getName().startsWith("module.latency")
                        || id.getName().startsWith("sun.latency")
                        || id.getName().startsWith("perf.latency")
                        || id.getName().startsWith("saturn.latency")
                        || id.getName().startsWith("ucPerf.latency")
                        || id.getName().startsWith("placementId.latency")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(0.95,0.98,0.99)
                            .build()
                            .merge(config);
                }*/
                if (id.getName().startsWith("reactor.flow.duration")
                        || id.getName().endsWith(".latency")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(0.95,0.98,0.99,0.999)
                            .build()
                            .merge(config);
                }
                return config;
            }
        });

        /**
         *  请求返回状态
         */
        //sunTimer = meterRegistry.timer("sun.latency");
        //perfTimer = meterRegistry.timer("perf.latency");
        //saturnTimer = meterRegistry.timer("saturn.latency");
        //placementIdTimer = meterRegistry.timer("placementId.latency");
        sunRRIpcClientFileCount = meterRegistry.gauge("sun.RR.ipc.client.file.count", new AtomicLong(0));
        wnIpcClientFileCount = meterRegistry.gauge("sun.wn.ipc.client.file.count", new AtomicLong(0));
        exchangeRcost = meterRegistry.gauge("dynamic.exchange.rcost", new AtomicDouble(0));
        demandRcost = meterRegistry.gauge("dynamic.demand.rcost", new AtomicDouble(0));
        CountService.init(meterRegistry);
    }
}