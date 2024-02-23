package com.inmobi.supply.sun.config.redis;

import com.inmobi.supply.component.model.redis.*;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.model.geo.GeoModel;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import java.time.Duration;

@Configuration
public class RedisConfiguration {
    @Bean("redisProperties0")
    @ConfigurationProperties(prefix = "spring.redis")
    public RedisProperties redisProperties0() {
        return new RedisProperties();
    }

    @Bean("redisPool0")
    @ConfigurationProperties(prefix = "spring.redis.lettuce.pool")
    public GenericObjectPoolConfig redisPool0() {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        return poolConfig;
    }

    @Primary
    @Bean("redisConnectionFactory0")
    public ReactiveRedisConnectionFactory getReactiveRedisConnectionFactory(@Qualifier("redisPool0") GenericObjectPoolConfig poolConfig,
                                                                            @Qualifier("redisProperties0") RedisProperties redisProperties) {
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();

        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort()), clientConfig);
    }

    @Primary
    @Bean("blockRedisConnectionFactory0")
    public RedisConnectionFactory getBlockRedisConnectionFactory0(@Qualifier("redisPool0") GenericObjectPoolConfig poolConfig,
                                                                    @Qualifier("redisProperties0") RedisProperties redisProperties) {
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();

        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort()), clientConfig);
    }

    @Bean("redisProperties1")
    @ConditionalOnProperty(prefix = "spring.redis1", value = "host")
    @ConfigurationProperties(prefix = "spring.redis1")
    public RedisProperties redisProperties1() {
        return new RedisProperties();
    }

    @Bean("redisPool1")
    @ConditionalOnProperty(prefix = "spring.redis1.lettuce.pool", value = "max-total")
    @ConfigurationProperties(prefix = "spring.redis1.lettuce.pool")
    public GenericObjectPoolConfig redisPool1() {
        return new GenericObjectPoolConfig();
    }

    @Bean("redisConnectionFactory1")
    @ConditionalOnProperty(prefix = "spring.redis1", value = "host")
    public ReactiveRedisConnectionFactory lettuceConnectionFactory(@Qualifier("redisPool1") GenericObjectPoolConfig poolConfig,
                                                                   @Qualifier("redisProperties1") RedisProperties redisProperties) {
        //没有配置第二个数据源
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();

        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort()), clientConfig);
    }

    @Bean("placementReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, PlacementModel> placementReactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<PlacementModel> placementBaseReactiveTemplate = new BaseReactiveTemplate<>();
        return placementBaseReactiveTemplate.getReactiveRedisTemplate(factory, PlacementModel.class);
    }

    @Bean("appReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, AppModel> appReactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<AppModel> appBaseReactiveTemplate = new BaseReactiveTemplate<>();
        return appBaseReactiveTemplate.getReactiveRedisTemplate(factory, AppModel.class);
    }

    @Bean("deviceModelReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, DeviceModel> deviceModelReactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<DeviceModel> deviceModelBaseReactiveTemplate = new BaseReactiveTemplate<>();
        return deviceModelBaseReactiveTemplate.getReactiveRedisTemplate(factory, DeviceModel.class);
    }

    @Bean("userAgentModelReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, UserAgentModel> userAgentModelReactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<UserAgentModel> userAgentModelReactiveRedisTemplate = new BaseReactiveTemplate<>();
        return userAgentModelReactiveRedisTemplate.getReactiveRedisTemplate(factory, UserAgentModel.class);
    }

    @Bean("reactiveStringRedisTemplate")
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        ReactiveRedisTemplate<String, String> reactiveRedisTemplate = new ReactiveRedisTemplate<String, String>(factory, RedisSerializationContext.string());
        return reactiveRedisTemplate;
    }

    @Bean("geoReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, GeoModel> geoReactiveRedisTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<GeoModel> geoReactiveRedisTemplate = new BaseReactiveTemplate<>();
        return geoReactiveRedisTemplate.getReactiveRedisTemplate(factory, GeoModel.class);
    }

    @Bean("reactiveRedisTemplate2")
    public ReactiveRedisTemplate<String, String> geTuiReactiveRedisTemplate(@Qualifier("redisConnectionFactory1") ReactiveRedisConnectionFactory factory) {
        ReactiveRedisTemplate<String, String> reactiveRedisTemplate = new ReactiveRedisTemplate<String, String>(factory, RedisSerializationContext.string());
        return reactiveRedisTemplate;
    }


    @Bean("appInfoBaseModelTemplate")
    public ReactiveRedisTemplate<String, AppInfoBaseModel> appInfoBaseModelTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<AppInfoBaseModel> geoReactiveRedisTemplate = new BaseReactiveTemplate<>();
        return geoReactiveRedisTemplate.getReactiveRedisTemplate(factory, AppInfoBaseModel.class);
    }

    @Bean("materialSandboxTemplate")
    public ReactiveRedisTemplate<String, MaterialSandbox> materialSandboxTemplate(@Qualifier("redisConnectionFactory0") ReactiveRedisConnectionFactory factory) {
        BaseReactiveTemplate<MaterialSandbox> geoReactiveRedisTemplate = new BaseReactiveTemplate<>();
        return geoReactiveRedisTemplate.getReactiveRedisTemplate(factory, MaterialSandbox.class);
    }

    @Bean("stringRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(@Qualifier("blockRedisConnectionFactory0") RedisConnectionFactory factory) {
        StringRedisTemplate stringStringRedisTemplate = new StringRedisTemplate();
        stringStringRedisTemplate.setConnectionFactory(factory);
        return stringStringRedisTemplate;
    }

}
