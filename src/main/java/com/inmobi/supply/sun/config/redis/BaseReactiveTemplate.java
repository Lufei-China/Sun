package com.inmobi.supply.sun.config.redis;

import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class BaseReactiveTemplate<T> {

    /**
     * 生成Template模板
     *
     * @param factory
     * @return
     */
    public ReactiveRedisTemplate<String, T> getReactiveRedisTemplate(ReactiveRedisConnectionFactory factory, Class<T> tClass) {
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer(tClass);
        RedisSerializationContext.RedisSerializationContextBuilder<String, T> builder = RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, T> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}