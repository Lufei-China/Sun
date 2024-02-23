package com.inmobi.supply.sun.service.preProc.validator.module;

import java.util.Optional;

import cn.inmobi.pb.api.OsType;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicIntegerConfig;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.dataFlow.ValidatorDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Gavin.xu
 * 2020.5.18
 * 手机操作系统版本校验
 */
@Service
@Slf4j
public class ValidatorIntegration implements ModuleWorker<ValidatorDataType> {

    @Override
    public Mono<ValidatorDataType> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(), "ValidatorIntegration");
            if (StringUtils.isNotBlank(runTimeContext.getDevice().getOsVersion())) {
                String osVersion = runTimeContext.getDevice().getOsVersion();
                OsType osType = runTimeContext.getDevice().getOsType();
                Integer majorVersion;
                try {
                    majorVersion = Integer.parseInt(Optional.ofNullable(StringUtils.split(osVersion, SymbolString.POINT)[0]).orElse(""));
                } catch (NumberFormatException e) {
                    PerfUtils.perf("version.clean.invalid");
                    throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
                }
                if (osType == OsType.IOS) {
                    if (DynamicBooleanConfig.VERSION_CLEAN_IOS_LOW_SUPPORT.get() && majorVersion < DynamicIntegerConfig.IOS_VERSION_MIN.get()) {
                        PerfUtils.perf("version.clean", Tag.of("os", "ios"), Tag.of("type", "low"));
                        throw new SunException(TermCode.IOS_VERSION_TOO_LOW, runTimeContext);
                    }
                    if (DynamicBooleanConfig.VERSION_CLEAN_IOS_HIGH_SUPPORT.get() && majorVersion > DynamicIntegerConfig.IOS_VERSION_MAX.get()) {
                        PerfUtils.perf("version.clean", Tag.of("os", "ios"), Tag.of("type", "high"));
                        throw new SunException(TermCode.IOS_VERSION_TOO_HIGH, runTimeContext);
                    }
                }
                if (osType == OsType.ANDROID) {
                    if (DynamicBooleanConfig.VERSION_CLEAN_ANDROID_LOW_SUPPORT.get() && majorVersion < DynamicIntegerConfig.ANDROID_VERSION_MIN.get()) {
                        PerfUtils.perf("version.clean", Tag.of("os", "android"), Tag.of("type", "low"));
                        throw new SunException(TermCode.ANDROID_VERSION_TOO_LOW, runTimeContext);
                    }
                    if (DynamicBooleanConfig.VERSION_CLEAN_ANDROID_HIGH_SUPPORT.get() && majorVersion > DynamicIntegerConfig.ANDROID_VERSION_MAX.get()) {
                        PerfUtils.perf("version.clean", Tag.of("os", "android"), Tag.of("type", "high"));
                        throw new SunException(TermCode.ANDROID_VERSION_TOO_HIGH, runTimeContext);
                    }
                }
            } else {
                PerfUtils.perf("version.clean.blank");
                throw new SunException(TermCode.OS_VERSION_INVALID, runTimeContext);
            }
            return Mono.just(new ValidatorDataType());
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }
}
