package com.inmobi.supply.sun.service.preProc.prepare;

import java.util.Map;

import cn.inmobi.pb.api.BidRequest;
import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.call.SendRequestService;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareAdPools;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidFloor;
import com.inmobi.supply.sun.service.preProc.prepare.module.PrepareBidRequest;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 初始化广告请求
 * 产出BidRequest对象，接下来用于请求广告池
 *
 * @author remi.li
 */
@Service
@Slf4j
public class PrepareRequestService extends Worker<EnrichmentDataType, BidRequest> {
    @Autowired
    private SendRequestService sendRequestService;
    @Autowired
    private PrepareBidFloor prepareBidFloor;
    @Autowired
    private PrepareAdPools prepareAdPools;
    @Autowired
    private PrepareBidRequest prepareBidRequest;

    @Override
    public Flux<BidRequest> doProcess(Context context, Flux<EnrichmentDataType> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        Timer.Sample stopWatch = PerfUtils.getWatcher();
        return monoDataFromLastPrc
                .flatMap(dataType -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "PrepareRequest");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    return prepareBidFloor.workMono(runTimeContext)
                            .flatMap(ctx -> prepareAdPools.workMono(runTimeContext)
                                    .flatMap(ctx1 -> {
                                        Mono<BidRequest> bidRequestMono = prepareBidRequest.workMono(runTimeContext);
                                        mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                        PerfUtils.perf("sun.prepareRequest", stopWatch);
                                        return bidRequestMono;
                                    }));
                });
    }

    @Override
    public Worker getNextProc() {
        return sendRequestService;
    }
}