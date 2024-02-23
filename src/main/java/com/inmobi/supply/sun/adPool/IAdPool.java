package com.inmobi.supply.sun.adPool;

import cn.inmobi.pb.api.BidRequest;
import cn.inmobi.pb.api.BidResponse;
import cn.inmobi.pb.api.SourceType;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiter;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.framework.common.utils.throttling.ThrottlingConfig;
import com.inmobi.supply.sun.config.AdPoolConfig;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.time.Duration;

/**
 * adPool接口i
 *
 * @author remi.li
 */
public interface IAdPool<T extends BidRequest, R extends BidResponse> {
    /**
     *  adPool sourceType
     *  PERF = 0;
     *  BRAND = 1;
     *  SATURN = 2;
     *  REMARKETING = 3;
     *  @return
     */
    SourceType sourceType();

    AdPoolConfig adPoolConfig();

    Mono<R> sendRequest(T adPoolRequest);

    /**
     * 超时时间配置
     */
    default Duration timeout() {
        return Duration.ofMillis(adPoolConfig().getTimeout());
    }

    /**
     * rateLimiter配置
     * @return
     */
    RateLimiterConfig rateLimiter();
}