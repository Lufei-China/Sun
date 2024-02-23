package com.inmobi.supply.sun.adPool;

import cn.inmobi.pb.api.BidRequest;
import cn.inmobi.pb.api.BidResponse;
import cn.inmobi.pb.api.ReactorRtbGrpc;
import cn.inmobi.pb.api.SourceType;
import com.framework.starter.grpc.client.GrpcClient;
import com.inmobi.supply.framework.common.utils.rateLimiter.RateLimiterConfig;
import com.inmobi.supply.sun.config.AdPoolConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.PERF_AD_POOL;
import static com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig.PERF_RATE_LIMITER;

/**
 * PerfAdPool
 *
 * @author remi.li
 */
@Service
public class PerfAdPool implements IAdPool<BidRequest, BidResponse> {
    @GrpcClient(name = "engine")
    private ReactorRtbGrpc.ReactorRtbStub rtbStub;

    @Override
    public SourceType sourceType() {
        return SourceType.PERF;
    }

    @Override
    public AdPoolConfig adPoolConfig() {
        return PERF_AD_POOL.get();
    }

    @Override
    public RateLimiterConfig rateLimiter() {
        return PERF_RATE_LIMITER.get();
    }

    @Override
    public Mono<BidResponse> sendRequest(BidRequest adPoolRequest) {
        return rtbStub.getMobileAd(adPoolRequest);
    }

    @Override
    public String toString() {
        return sourceType().name();
    }
}