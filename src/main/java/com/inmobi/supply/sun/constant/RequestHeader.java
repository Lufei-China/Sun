package com.inmobi.supply.sun.constant;

/**
 * 请求携带的header string集中定义
 *
 * 注意！！！！！！！！！
 * 从runtimeContext header里去这些header的时候，统一要变成小写！！！！！
 * @author remi.li
 */
public class RequestHeader {
    //是否追踪请求
    //public static final String TRACE_ENABLED_HEADER = "x-inmobi-trace";
    //是否是测试环境
    public static final String TEST_ENV_HEADER = "x-inmobi-test-env";
    /**
     * ip相关
     */
    public static final String X_FORWARD_FOR = "X-Forwarded-For";
    public static final String X_INMOBI_FORWARD_FOR = "X-Inmobi-Forwarded-For";
    public static final String X_INMOBI_LB_FORWARD_FOR = "X-Inmobi-LB-Forwarded-For";
    public static final String X_INMOBI_REMOTE_IP = "X-Inmobi-Remote-Ip";
    public static final String X_INMOBI_NGINX_FORWARDED_FOR = "x-inmobi-nginx-forwarded-for";
    public static final String X_REAL_IP="X-Real-Ip";
    public static final String X_Inmobi_IP="X-Inmobi-Ip";
    public static final String GATEWAY_IP = "Gateway-Ip";
    /**
     * host相关
     */
    public static final String X_HTTP_HOST = "X-Inmobi-Host";
    public static final String X_HOST = "Host";
    /**
     * https相关
     */
    public static final String X_SECURE_REQUEST = "x-secure-request";
    public static final String X_FORWARD_PROTO = "X-Forwarded-Proto";
    /**
     * ua 相关
     */
    public static final String NORMAL_USER_AGENT = "User-Agent";

    public static final String TRACE_ENABLED_HEADER = "x-inmobi-adserving-enable-trace";
    public static final String V4_TRACE_ENABLED_HEADER = "x-inmobi-trace";
    public static final String GETUI_ACCEPT_HEADER = "application/vnd.dmp.v3+json";

    public static final String NO_VALIDATE_HEADER = "x-inmobi-adserving-request-no-validate";

    public static final String MOCK_AD_HEADER = "mock-ad-request";
    public static final String MOCK_AD_INTERACTION_HEADER = "mock-ad-request-interaction";
    public static final String MOCK_AD_CREATIVE_TYPE_HEADER = "mock-ad-request-creative-type";
}