package com.inmobi.supply.sun.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.inmobi.supply.framework.common.utils.SpringUtils;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import io.micrometer.core.instrument.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * perf utils class
 * use for metrics
 *
 * @author remi.li
 */
public class PerfUtils {
    private static final PrometheusOverlord prometheusOverlord = SpringUtils.getApplicationContext().getBean(PrometheusOverlord.class);

    /**
     * perf by name
     */
    public static void perf(String name) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        Counter counter = prometheusOverlord.getMeterRegistry().counter(name);
        counter.increment();
    }

    /**
     * perf by name and tag
     */
    public static void perf(String name, String tagKey, String tagValue) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        if (StringUtils.isBlank(tagKey)) {
            perf(name);
            return;
        }
        if (StringUtils.isNotBlank(tagKey) && StringUtils.isBlank(tagValue)) {
            throw new RuntimeException("empty of perf tag value, which key is " + tagKey);
        }
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        Counter.Builder counterBuilder = Counter.builder(name);
        Counter counter = counterBuilder.tag(tagKey, tagValue).register(meterRegistry);
        counter.increment();
    }

    /**
     * perf by name and tag
     */
    public static void perf(String name, Tag... tags) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        if (ArrayUtils.isEmpty(tags)) {
            perf(name);
            return;
        }
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        Counter.Builder counterBuilder = Counter.builder(name);
        Counter counter = counterBuilder.tags(Arrays.asList(tags)).register(meterRegistry);
        counter.increment();
    }

    /**
     * perf by name and tag list
     */
    public static void perf(String name, List<Tag> tags) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        if (CollectionUtils.isEmpty(tags)) {
            perf(name);
            return;
        }
        tags = tags.stream()
                .filter(tag -> StringUtils.isNotBlank(tag.getKey()) && StringUtils.isNotBlank(tag.getValue())).collect(Collectors.toList());
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        Counter.Builder counterBuilder = Counter.builder(name);
        Counter counter = counterBuilder.tags(tags).register(meterRegistry);
        counter.increment();
    }

    /**
     * perf count by name
     * use to metrics collection size
     * @param name
     * @param collection
     */
    public static void perfCount(String name, Collection<?> collection) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        Gauge.builder(name, collection, value -> (double) value.size()).register(meterRegistry);
    }

    /**
     * perf count by name and tag
     * @param name
     * @param collection
     * @param tagKey
     * @param tagValue
     */
    public static void perfCount(String name, Collection<?> collection, String tagKey, String tagValue) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        if (StringUtils.isBlank(tagKey)) {
            perfCount(name, collection);
            return;
        }
        if (StringUtils.isNotBlank(tagKey) && StringUtils.isBlank(tagValue)) {
            throw new RuntimeException("empty of perf tag value, which key is " + tagKey);
        }
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        Gauge.builder(name, collection, value -> (double) value.size())
                .tag(tagKey, tagValue)
                .register(meterRegistry);
    }

    public static Timer.Sample getWatcher() {
        Timer.Sample sample = Timer.start();
        return sample;
    }

    /**
     * time perf by name
     * @param name
     * @param stopWatch
     */
    public static void perf(String name, Timer.Sample stopWatch) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of perf name");
        }
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        stopWatch.stop(meterRegistry.timer(name));
    }

    /**
     * time perf by name and tag
     * @param name
     * @param stopWatch
     */
    public static void perf(String name, Timer.Sample stopWatch, String tagKey, String tagValue) {
        if (StringUtils.isBlank(name)) {
            throw new RuntimeException("empty of prf name");
        }
        if (StringUtils.isBlank(tagKey)) {
            perf(name, stopWatch);
            return;
        }
        if (StringUtils.isNotBlank(tagKey) && StringUtils.isBlank(tagValue)) {
            throw new RuntimeException("empty of perf tag value, which key is " + tagKey);
        }

        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        stopWatch.stop(meterRegistry.timer(name, tagKey, tagValue));
    }

    public static void timer(String name, long time) {
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        meterRegistry.timer(name).record(time, TimeUnit.MILLISECONDS);
    }

    public static void timer(String name, long time, Tag... tags) {
        MeterRegistry meterRegistry = prometheusOverlord.getMeterRegistry();
        meterRegistry.timer(name, Arrays.asList(tags)).record(time, TimeUnit.MILLISECONDS);
    }
}