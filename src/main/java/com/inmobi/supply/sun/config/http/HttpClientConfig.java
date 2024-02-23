package com.inmobi.supply.sun.config.http;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * @author zeki.zhao
 */
@Configuration
@Component
public class HttpClientConfig {
    @Primary
    @Bean
    public HttpClient getHttpClient() {
        //100k 缓冲区
        int bufferSize = 100 * 1024;
        ConnectionProvider provider =
                ConnectionProvider.builder("sun_connection_provider")
                        .maxConnections(10000)
                        .pendingAcquireTimeout(Duration.ofMillis(3000))
                        .maxIdleTime(Duration.ofMillis(3000))
                        .build();
        HttpClient httpClient = HttpClient.create(provider)
                .keepAlive(true)
                .tcpConfiguration(tpcClient ->
                        tpcClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50)
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.TCP_NODELAY, true)
                                .option(ChannelOption.SO_SNDBUF, bufferSize)
                                .option(ChannelOption.SO_RCVBUF, bufferSize)
                )
                .metrics(true);
        return httpClient;
    }
}