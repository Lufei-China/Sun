package com.inmobi.supply.sun;

import com.inmobi.supply.sun.service.mainProc.SunAdsManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * @author remi.li
 *
 * router entrance of Sun system
 */
@Configuration
@Slf4j
public class Router {
    /**
     * sdk router
     * @param handler
     * @return
     */
    @Bean
    public RouterFunction<ServerResponse> routesSdk(SunAdsManager handler) {
        return route(request -> {
            MediaType contentType = request.headers().contentType().orElse(null);
            return request.path().equals("/showad.asm")
                    && contentType != null
                    && (contentType.equals(MediaType.TEXT_PLAIN) || contentType.equals(MediaType.APPLICATION_FORM_URLENCODED));
        }, handler::begin);
    }

    /**
     * apiv.* / customApiApi router
     * @param handler
     * @return
     */
    @Bean
    public RouterFunction<ServerResponse> routesApi(SunAdsManager handler) {
        return route(request -> {
            MediaType contentType = request.headers().contentType().orElse(null);
            return request.path().startsWith("/showad/")
                    && contentType != null
                    && (StringUtils.lowerCase(contentType.toString()).startsWith("application/json")
                    || StringUtils.lowerCase(contentType.toString()).startsWith("application/x-protobuf")
                    || StringUtils.lowerCase(contentType.toString()).startsWith("application/octet-stream"));
        }, handler::begin);
    }
}