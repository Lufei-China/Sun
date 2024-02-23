package com.inmobi.supply.sun.service.preProc.supplySelection;

import java.util.Map;

import com.inmobi.supply.sun.constant.PerfConstant;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.dataFlow.NappDataType;
import com.inmobi.supply.sun.model.dataFlow.SupplySelectionDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.enrichment.EnricherService;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Supply Selection Service
 * @author remi.li
 */
@Slf4j
@Service
public class SupplySelectionService extends Worker<NappDataType, SupplySelectionDataType> {
    @Autowired
    private EnricherService enricherService;

    @Override
    public Flux<SupplySelectionDataType> doProcess(Context context, Flux<NappDataType> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return monoDataFromLastPrc
                .flatMap(nappDataType -> {
                    PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "SupplySelectionService");
                    RunTimeContext runTimeContext = getUpperRunTimeContext(context);
                    mapNewContext.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
                    return Mono.just(new SupplySelectionDataType());
        });
    }

    @Override
    public Worker getNextProc() {
        return enricherService;
    }
}