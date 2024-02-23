package com.inmobi.supply.sun.config.redis;

public class RedisProperties {
    private String host;
    private int port;

    public RedisProperties() {
    }

    public RedisProperties(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
