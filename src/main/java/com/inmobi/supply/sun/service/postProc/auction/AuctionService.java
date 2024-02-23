package com.inmobi.supply.sun.service.postProc.auction;

import java.util.ArrayList;
import java.util.Map;

import cn.inmobi.pb.api.BidResponse;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.postProc.auction.module.AuctionBidResponse;
import com.inmobi.supply.sun.service.postProc.auction.module.DynamicFloor;
import com.inmobi.supply.sun.service.postProc.format.EnvelopeService;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;


@Slf4j
@Service
public class AuctionService extends Worker<BidResponse, AuctionDataType> {
    @Autowired
    private EnvelopeService envelopeService;
    @Autowired
    private AuctionBidResponse auctionBidResponse;
    @Autowired
    private DynamicFloor dynamicFloor;

    @Override
    public Flux<AuctionDataType> doProcess(Context context, Flux<BidResponse> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .collectList()
                .publishOn(Schedulers.parallel())
                .defaultIfEmpty(new ArrayList<>())
                .flatMap(bidResponseList -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Auction");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    if(CollectionUtils.isNotEmpty(bidResponseList)) {
                        runTimeContext.getValidBidResponseList().addAll(bidResponseList);
                    }
                    return auctionBidResponse.workMono(runTimeContext).flatMap(ctx -> {
                        Mono<AuctionDataType> auctionServiceMono = dynamicFloor.processMono(runTimeContext);
                        mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                        return auctionServiceMono;
                    });
                }).flux();
    }

    @Override
    public Worker getNextProc() {
        return envelopeService;
    }
}