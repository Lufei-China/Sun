package com.inmobi.supply.sun;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.framework.starter.sentinel.config.EnableSentinel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication(scanBasePackages = {"com.inmobi.supply"},
        exclude = {RedisAutoConfiguration.class,
                RedisReactiveAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class})
@EnableApolloConfig
@EnableSentinel
public class Application {
    public static void main(String[] args) {
        try {
            Schedulers.enableMetrics();
            SpringApplication.run(Application.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}