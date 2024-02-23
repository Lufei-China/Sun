package com.inmobi.supply.sun.service.preProc.normalizer.module;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicMapBooleanConfig.API_RAWPATH_ISUSED;
import static com.inmobi.supply.sun.config.dynamic.DynamicMapBooleanConfig.SDK_RAWPATH_ISUSED;
import static com.inmobi.supply.sun.constant.RequestHeader.GATEWAY_IP;

/**
 * extract Host and Url
 * determine requestType
 *
 * @author Gavin.xu
 * @date 2020.5.18
 */
@Service
@Slf4j
public class NormalizerHostAndUrl implements ModuleWorker<Integer> {
    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(), "all headers {}", runTimeContext.getHeaders());
            if (StringUtils.isNotBlank(runTimeContext.getHeaders().get(GATEWAY_IP.toLowerCase()))) {
                PerfUtils.perf("gateway.ip.requests", Tag.of("ip", runTimeContext.getHeaders().get(GATEWAY_IP.toLowerCase())));
            }
            ServerRequest request = runTimeContext.getServerRequest();
            URI uri = request.uri();
            // get host from header or URI
            String host = getHost(runTimeContext);
            String rawPath = uri.getRawPath();
            if (StringUtils.isBlank(rawPath)) {
                throw new SunException(TermCode.INVALID_PATH, runTimeContext);
            }
            runTimeContext.setHost(host);
            //if https
            runTimeContext.setHttps(isHttpsRequest(runTimeContext));
            runTimeContext.setRawPath(uri.getRawPath());

            RequestType requestType = getRequestType(runTimeContext, host, uri.getRawPath());

            runTimeContext.setRequestType(requestType);
            MediaType contentType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.TEXT_PLAIN);
            if (requestType == RequestType.IN_VALID) {
                //监控 是不是放了不支持/异常的量进来
                LogUtils.logInfo(runTimeContext.getRequestId(), "invalid requestPath {} contentType {}", uri, contentType);
                throw new SunException(TermCode.INVALID_REQUEST_TYPE, runTimeContext);
            }
            return Mono.just(0);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private String getHost(RunTimeContext runTimeContext) {
        Map<String, String> headers = runTimeContext.getHeaders();
        String xInmobiHost = headers.get(StringUtils.lowerCase(RequestHeader.X_HTTP_HOST));
        if (StringUtils.isNotEmpty(xInmobiHost)) {
            return xInmobiHost;
        }
        String xHost = headers.get(StringUtils.lowerCase(RequestHeader.X_HOST));
        if (StringUtils.isNotEmpty(xHost)) {
            return xHost;
        }
        return runTimeContext.getServerRequest().uri().getHost();
    }

    private boolean isHttpsRequest(RunTimeContext runTimeContext) {
        Map<String, String> headers = runTimeContext.getHeaders();
        boolean isSecure = SymbolString.TRUE.equalsIgnoreCase(headers.get(StringUtils.lowerCase(RequestHeader.X_SECURE_REQUEST))) ||
                SymbolString.HTTPS.equalsIgnoreCase(headers.get(StringUtils.lowerCase(RequestHeader.X_FORWARD_PROTO)));
        if (!isSecure) {
            isSecure = runTimeContext.getServerRequest().uri().getScheme().equals(SymbolString.HTTPS);
        }
        return isSecure;
    }

    private RequestType getRequestType(RunTimeContext runTimeContext, String host, String rawPath) {
        RequestType requestType = RequestType.IN_VALID;
        Map<String, Boolean> sdkrawPathSwitch = SDK_RAWPATH_ISUSED.get().getOrDefault(host, new HashMap<>());
        Map<String, Boolean> apiPathSwitch = API_RAWPATH_ISUSED.get().getOrDefault(host, new HashMap<>());
        LogUtils.logInfo(runTimeContext.getRequestId(), "host={} path={} apiPathSwitch={}", host, rawPath, apiPathSwitch);
        switch (host) {
            case RuntimeConstant.SDK_ENDPOINT_JS:
            case RuntimeConstant.SDK_ENDPOINT_W:
            case RuntimeConstant.SDK_ENDPOINT_MK:
                if (sdkrawPathSwitch.getOrDefault(RuntimeConstant.DEFAULT_RAW_PATH, false)) {
                    requestType = RequestType.SDK;
                }
                break;
            case RuntimeConstant.SDK_ENDPOINT:
                if (sdkrawPathSwitch.getOrDefault(rawPath, false)) {
                    requestType = RequestType.SDK;
                }
                break;
            case RuntimeConstant.SDK_ENDPOINT_VAST:
                if (apiPathSwitch.getOrDefault(RuntimeConstant.DEFAULT_RAW_PATH, false)) {
                    requestType = RequestType.API_VAST_3_0;
                }
                break;
            case RuntimeConstant.SDK_ENDPOINT_C2S:
                if (rawPath.equals("/showad/v3.1")) {
                    if (apiPathSwitch.getOrDefault(rawPath, false)) {
                        requestType = RequestType.API_3_1;
                    }
                } else {
                    if (apiPathSwitch.getOrDefault(RuntimeConstant.DEFAULT_RAW_PATH, false)) {
                        requestType = RequestType.SDK;
                    }
                }
                break;
            case RuntimeConstant.API_ENDPOINT:
            case RuntimeConstant.API_UAT_ENDPOINT:
                if (rawPath.startsWith("/showad/")) {
                    String version = rawPath.substring("/showad/".length());
                    boolean apiIsUsed = apiPathSwitch.getOrDefault(rawPath, false);
                    boolean customIsUsed = apiPathSwitch.getOrDefault("custom", false);
                    switch (version) {
                        case "v2": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_2_0;
                            }
                            break;
                        }
                        case "v2.1": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_2_1;
                            }
                            break;
                        }
                        case "v3": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_0;
                            }
                            break;
                        }
                        case "v3.1": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_1;
                            }
                            break;
                        }
                        case "v3/vast": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_VAST_3_0;
                            }
                            break;
                        }
                        case "v3.2": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_2;
                            }
                            break;
                        }
                        case "v3.3": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_3;
                            }
                            break;
                        }
                        case "v3.3.1": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_3_1;
                            }
                            break;
                        }
                        case "v3.4": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_4;
                            }
                            break;
                        }
                        case "v3.4.1": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_3_4_1;
                            }
                            break;
                        }
                        case "v4.0": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_4_0;
                            }
                            break;
                        }
                        case "ortb": {
                            if (apiIsUsed) {
                                requestType = RequestType.API_ORTB_1_0;
                            }
                            break;
                        }
                        default: {
                            if (customIsUsed) {
                                PerfUtils.perf("customApi", "version", version);
                                requestType = RequestType.CUSTOM_API;
                            }
                            break;
                        }
                    }
                } else {
                    //TODO
                    requestType = RequestType.SDK;
                }
                break;
            case RuntimeConstant.MOPUB_API_ENDPOINT:
            case RuntimeConstant.ZHIHU_API_ENDPOINT:
                if (apiPathSwitch.getOrDefault(RuntimeConstant.DEFAULT_RAW_PATH, false)) {
                    requestType = RequestType.CUSTOM_API;
                }
                break;
            default:
                break;
        }
        return requestType;
    }
}
