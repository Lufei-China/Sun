package com.inmobi.supply.sun.service.preProc.enrichment;

import java.util.Map;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.dataFlow.SupplySelectionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichCaid;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnrichGeo;
import com.inmobi.supply.sun.service.preProc.enrichment.module.EnricherGeTui;
import com.inmobi.supply.sun.service.preProc.prepare.PrepareRequestService;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;


@Service
@Slf4j
public class EnricherService extends Worker<SupplySelectionDataType, EnrichmentDataType> {
    @Autowired
    private EnricherGeTui enricherGeTui;

    @Autowired
    private EnrichGeo enrichGeo;

    @Autowired
    private EnrichCaid enrichCaid;

    @Autowired
    private PrepareRequestService prepareRequestService;

    @Override
    public Flux<EnrichmentDataType> doProcess(Context context, Flux<SupplySelectionDataType> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .flatMap(dataType -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "Enrichment");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    return enricherGeTui.workMono(runTimeContext)
                            .flatMap(ctx -> enrichGeo.workMono(runTimeContext)
                                    .flatMap(ctx1 -> {
                                            Mono<EnrichmentDataType> enrichmentDataTypeMono = enrichCaid.workMono(runTimeContext);
                                            mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                                            return enrichmentDataTypeMono;
                                        }));
        });
    }

    @Override
    public Worker getNextProc() {
        return prepareRequestService;
    }
}