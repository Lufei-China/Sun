
package com.inmobi.supply.sun.adPool;

import cn.inmobi.pb.api.BidRequest;
import cn.inmobi.pb.api.BidResponse;
import cn.inmobi.pb.api.SourceType;
import cn.inmobi.pb.saturn.ReactorSaturnRtbGrpc;
import com.framework.starter.grpc.client.GrpcClient;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.sun.config.AdPoolConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.SATURN_AD_POOL;
import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.SATURN_RATE_LIMITER;

/**
 * saturn ad pool
 *
 * @author remi.li
 */
@Service
public class SaturnAdPool implements IAdPool<BidRequest, BidResponse> {
    @GrpcClient(name = "saturn")
    private ReactorSaturnRtbGrpc.ReactorSaturnRtbStub saturnRtbStub;

    @Override
    public SourceType sourceType() {
        return SourceType.SATURN;
    }

    @Override
    public AdPoolConfig adPoolConfig() {
        return SATURN_AD_POOL.get();
    }

    @Override
    public Mono<BidResponse> sendRequest(BidRequest adPoolRequest) {
        return saturnRtbStub.getMobileAd(adPoolRequest);
    }

    @Override
    public RateLimiterConfig rateLimiter() {
        return SATURN_RATE_LIMITER.get();
    }

    @Override
    public String toString() {
        return sourceType().name();
    }
}