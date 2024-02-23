package com.inmobi.supply.sun.service.preProc.normalizer.module;

import cn.inmobi.pb.sun.rr.base.Device;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.SdkParam;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.LatLong;
import com.inmobi.supply.sun.model.dataFlow.NormalizerDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Gavin.xu
 * 2020.5.18
 * 请求所在地址经纬度
 */
@Service
@Slf4j
public class NormalizerLatLong implements ModuleWorker<NormalizerDataType> {
    @Override
    public Mono<NormalizerDataType> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"NormalizerLatLong");
            if (runTimeContext.getRequestType().isSdk()) {
                LogUtils.logInfo(runTimeContext.getRequestId(),"NormalizerLatLongSDK");
                handleSdkLatLong(runTimeContext);
            } else {
                handleApiV3LatLong(runTimeContext);
            }
            return Mono.just(new NormalizerDataType());
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

    private void handleApiV3LatLong(RunTimeContext runTimeContext) {
        PerfUtils.perf("sun.publisher.latlon", Tag.of("type", "all"), Tag.of("from", "api"));
        if (runTimeContext.getBaseRequest().getDevice() != null && runTimeContext.getBaseRequest().getDevice().getGeo() != null) {
            Device.Geo geo = runTimeContext.getBaseRequest().getDevice().getGeo();
            runTimeContext.setLatLong(new LatLong(geo.getLat(), geo.getLon(), geo.getAccu()));
            PerfUtils.perf("sun.publisher.latlon", Tag.of("type", "receive"), Tag.of("from", "api"));
        }
    }

    private void handleSdkLatLong(RunTimeContext runTimeContext) {
        PerfUtils.perf("sun.publisher.latlon", Tag.of("type", "all"), Tag.of("from", "sdk"));
        String latLongStr = runTimeContext.getSdkRequest().get(SdkParam.U_LATLONG_ACCU.getParam());
        if (StringUtils.isNotBlank(latLongStr)) {
            String[] items = StringUtils.split(latLongStr, SymbolString.COMMA);
            if (items.length >= 2) {
                double reqLat = parseDouble(items[0]);
                double reqLon = parseDouble(items[1]);
                if (badDouble(reqLat) || badDouble(reqLon)) {
                    //log.warn("Normalizer NormalizerLatLong latlon incorrect:{}", latLongStr);
                }
                double scala = 0;
                if (items.length == 3) {
                    scala = parseDouble(items[2]);
                }
                runTimeContext.setLatLong(new LatLong(reqLat, reqLon, scala));
                PerfUtils.perf("sun.publisher.latlon", Tag.of("type", "receive"), Tag.of("from", "api"));
            }
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            //log.warn("NormalizerLatLong parse double error!");
        }
        return 0D;
    }

    private boolean badDouble(double num) {
        return Double.isNaN(num) || Double.isInfinite(num);
    }

    private boolean badInteger(int num) {
        return num > 0 && num < 10;
    }
}
