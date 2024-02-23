package com.inmobi.supply.sun.service.postProc.auction;


import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.AuctionDataType;
import com.inmobi.supply.sun.model.dataFlow.SendRequestDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.postProc.auction.module.AuctionBidResponse;
import com.inmobi.supply.sun.service.postProc.auction.module.AuctionMarketPlaceResponse;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class Auction2Service extends Worker2<SendRequestDataType, AsyncServletResponse> {
    @Autowired
    private Worker2<AuctionDataType, AsyncServletResponse> nextWorker;

    @Autowired
    private AuctionBidResponse auctionBidResponse;

    @Autowired
    private AuctionMarketPlaceResponse auctionMarketPlaceResponse;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(SendRequestDataType prepareRequestDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "AuctionService");
                LogUtils.logInfo(runTimeContext.getRequestId(),"enter Auction2Service");

                //配置 marketPlace 的广告位
                if (runTimeContext.getSwitchFlag() == 1) {
                    PerfUtils.perf("sun.mkt.place.count", "status", "response");
                    return auctionMarketPlaceResponse.workMono(runTimeContext);
                }
                return auctionBidResponse.workMono(runTimeContext);
            }
            return Mono.just(new AuctionDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new AuctionDataType());
        })).transform(nextWorker.transform());
    }

}