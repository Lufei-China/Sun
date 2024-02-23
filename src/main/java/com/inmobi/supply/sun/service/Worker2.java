package com.inmobi.supply.sun.service;

import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import java.util.function.Function;

/**
 * kernel work process
 *
 * @param <T> input dataType
 * @param <R> output dataType
 * @author remi.li
 */
@SuppressWarnings({"unchecked", "warning"})
@Slf4j
public abstract class Worker2<T, R> {

    public final static String CONTEXT_KEY = "rtKey";

    /**
     * saturn 统一转换类
     * @return
     */
    public Function<Mono<T>, Mono<R>> transform() {
        return f -> f.flatMap(this::apply);
    }

    /**
     * 抽象核心方法，需要重写
     * @param t
     * @return
     */
    protected abstract Mono<? extends R> apply(T t);

    protected boolean ifRunWorker(RunTimeContext runTimeContext){
        return !runTimeContext.isError();
    }

}