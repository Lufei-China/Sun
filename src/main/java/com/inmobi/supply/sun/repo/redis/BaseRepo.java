package com.inmobi.supply.sun.repo.redis;

import java.time.Duration;
import java.util.Random;

import javax.annotation.PostConstruct;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BaseRepo<T> {
    long DEFAULT_DELAY_SECONDS = 60L;
    long DEFAULT_INTERVAL_SECONDS = 180L;
    Random random = new Random();

    default int size() {
        return 0;
    }

    Publisher<T> loadRepo();

    @PostConstruct
    default void init() {
        Publisher repo = loadRepo();
        if (repo instanceof Mono) {
            Mono.from(loadRepo()).block();
        }
        if (repo instanceof Flux) {
            Flux.from(loadRepo()).blockLast();
        }
    }

    /**
     * 任务延迟执行秒数
     * @return
     */
    default Duration delaySeconds() {
        int randSec = random.nextInt(50);
        return Duration.ofSeconds(DEFAULT_DELAY_SECONDS + randSec);
    }

    /**
     * 任务间隔执行秒数
     * @return
     */
    default Duration intervalSeconds() {
        return Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS);
    }
}