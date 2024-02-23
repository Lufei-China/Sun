package com.inmobi.supply.sun.service.preProc.validator.module;

import cn.inmobi.pb.api.Carrier;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.DeviceType;
import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.redis.DeviceModel;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.PlacementRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset;
import com.inmobi.supply.sun.service.preProc.validator.common.UaParser;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.inmobi.supply.sun.constant.PerfConstant.REQUEST_OS_TYPE;
import static com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset.H;
import static com.inmobi.supply.sun.service.preProc.validator.common.AbstractHandset.W;


/**
 * Gavin.xu
 * 2020.5.18
 * 手机型号获取和UA校验
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class ValidatorTraffic implements ModuleWorker<Integer> {

    @Autowired
    PlacementRepo placementRepo;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"ValidatorTraffic");
            //是否是屏蔽的广告位，不记录日志，直接丢弃
            if (placementRepo.isBlockPlacement(runTimeContext.getPlacementId())){
                PerfUtils.perf("sun.block.qps");
                throw new SunException(TermCode.BlOCK_BY_ANALYSIS);
            }
            return Mono.just(1);
        }  catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }


}