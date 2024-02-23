package com.inmobi.supply.sun.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptBean {

    @Bean
    RedisScript<String> redisScript(){
        RedisScript<String> redisScript = new DefaultRedisScript<>();
        ((DefaultRedisScript<String>) redisScript).setResultType(String.class);
        ((DefaultRedisScript<String>) redisScript).setScriptText(
                "local val = redis.call('get', KEYS[1]) " +
                        "if not val then " +
                        "  redis.call('set', KEYS[1], '1') " +
                        "  redis.call('expire', KEYS[1], tonumber(ARGV[2])) " +
                        "  return '1' " +
                        "elseif tonumber(val) >= tonumber(ARGV[1]) then " +
                        "  return '2' " +
                        "else " +
                        "  redis.call('incr', KEYS[1]) " +
                        "end " +
                        "return '1'");
        return  redisScript;
    }
}

