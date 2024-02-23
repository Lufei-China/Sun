package com.inmobi.supply.sun.service.preProc.validator.module;

import cn.inmobi.pb.api.ConnectionType;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import reactor.core.publisher.Mono;

public class ValidatorPlacementCustomize implements ModuleWorker<ValidatorDataType> {
    @Override
    public Mono<ValidatorDataType> processMono(RunTimeContext runTimeContext) {
        PlacementModel placementModel = runTimeContext.getPlacementModel();
        ConnectionType connectionType = runTimeContext.getDevice().getConnectType();




        return null;
    }
}
