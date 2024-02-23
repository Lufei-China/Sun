package com.inmobi.supply.sun;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 功能概述
 * className:      ReactorTest
 * package:        com.inmobi.supply.sun
 * author:         Gavin.Xu
 * date:           2021/8/23
 */
@Slf4j
public class ReactorTest {

    @Test
    public void test1() {
        Mono.just(1).flatMap((Function<Integer, Mono<Integer>>) integerMono -> {
            log.info("111");
            System.out.println(1 / 0);
            return Mono.just(0);
        }).onErrorResume((Function<Throwable, Mono<Integer>>) throwable -> {
            System.out.println("error1");
            return Mono.just(2);
        }).onErrorResume((Function<Throwable, Mono<Integer>>) throwable -> {
                    System.out.println("error2");
                    return Mono.just(4);
                })
                .subscribe(p -> System.out.println(p.intValue()))
        ;
    }
}
