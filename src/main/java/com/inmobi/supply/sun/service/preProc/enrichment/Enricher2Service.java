package com.inmobi.supply.sun.service.preProc.enrichment;


import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.dataFlow.NappDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichAppList;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichCaid;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichGeo;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnricherGeTui;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class Enricher2Service extends Worker2<NappDataType, AsyncServletResponse> {
    @Autowired
    private EnricherGeTui enricherGeTui;

    @Autowired
    private EnrichGeo enrichGeo;

    @Autowired
    private EnrichCaid enrichCaid;

    @Autowired
    private EnrichAppList enrichAppList;

    @Autowired
    private Worker2<EnrichmentDataType, AsyncServletResponse> nextWorker;

    @Override
    protected Mono<? extends AsyncServletResponse> apply(NappDataType nappDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                LogUtils.logInfo(runTimeContext.getRequestId(),"Enricher2Service");
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "EnricherService");
                return enricherGeTui.workMono(runTimeContext)
                        .flatMap(ctx -> enrichGeo.workMono(runTimeContext)
                                .flatMap(ctx1 -> enrichCaid.workMono(runTimeContext)
                                        .flatMap(ctx2 -> enrichAppList.workMono(runTimeContext))));
            }
            return Mono.just(new EnrichmentDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {
            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            return Mono.just(new EnrichmentDataType());
        })).transform(nextWorker.transform());
    }

}