package com.inmobi.supply.sun.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

/**
 * kernel work process
 *
 * @param <T> input dataType
 * @param <R> output dataType
 * @author remi.li
 */
@SuppressWarnings({"unchecked", "warning"})
@Slf4j
public abstract class Worker<T, R> {
    /**
     * 核心工作流程
     * 不可以重写
     * @return Function
     */
    public final Function<Flux<T>, Flux<AsyncServletResponse>> kernelProcess() {
        final Map<Integer, RunTimeContext> newRunTimeTaskNorm = new HashMap<>();
        return flux -> flux.transform(fluxData -> {
            Flux<AsyncServletResponse> publisher = Mono.subscriberContext().flux()
                    .flatMap(context -> doProcess(context, fluxData, newRunTimeTaskNorm)
                            .elapsed().flatMap(tuple2 -> {
                        PerfUtils.timer("module.latency", tuple2.getT1(), Tag.of("service", this.getClass().getSimpleName()));
                        return Flux.just(tuple2.getT2());
                    }))
                    .onErrorResume(errorResumeFun(newRunTimeTaskNorm));
            Worker nextProc = getNextProc();
            if (nextProc != null) {
                return publisher
                        .transform(nextProc.kernelProcess())
                        .subscriberContext(context -> putDownStreamRunTimeContext((Context) context, newRunTimeTaskNorm));
            } else {
                //final worker service
                return publisher;
            }
        }).onErrorResume((Function<Throwable, Flux<AsyncServletResponse>>) exception -> {
            if (exception instanceof SunException) {
                SunException sunException = (SunException) exception;
                AsyncServletResponse asyncServletResponse = sunException.getRunTimeContext().getAsyncServletResponse();
                return Flux.just(asyncServletResponse);
            } else if (exception instanceof BlockException) {
                PerfUtils.perf("sunEntrance.block");
                AsyncServletResponse asyncServletResponse = new AsyncServletResponse();
                asyncServletResponse.setStatus(HttpStatus.NO_CONTENT);
                return Flux.just(asyncServletResponse);
            } else {
                log.error("kernelProcess error", exception);
                return Flux.just(new AsyncServletResponse());
            }
        });
    }

    private Function errorResumeFun(Map<Integer, RunTimeContext> newRunTimeTaskNorm) {
        return (Function<Throwable, Publisher<R>>) exception -> {
            if (exception instanceof SunException) {
                SunException sunException = (SunException) exception;
                RunTimeContext runTimeContext = sunException.getRunTimeContext();
                //runTimeContext.getTermCodes().add(sunException.getTermCode());
                //下游模块可以在已经出现异常的基础上，进行上下文操作
                //比如RR模块可以在流程真正终止之前发送RR日志
                try {
                    processIfError(runTimeContext, sunException, newRunTimeTaskNorm);
                } catch (Exception e) {
                    return Mono.error(e);
                }
                //异常继续在模块之间传递
                return Mono.error(sunException);
            } else {
                log.error("errorResumeFun exception", exception);
                return Mono.error(exception);
            }
        };
    }

    /**
     * 下一阶段worker
     */
    public abstract Worker getNextProc();

    /**
     * 如果上游抛出异常
     * 下游模块可以使用此方法，分析/添加全局上下文
     *
     * @param rtx                RunTimeContext
     * @param newRunTimeTaskNorm Map<Integer, RunTimeContext>
     */
    public void processIfError(RunTimeContext rtx, SunException sunException, Map<Integer, RunTimeContext> newRunTimeTaskNorm) throws Exception {
        //default do nothing
    }

    /**
     * 使用此方法书写具体逻辑
     * 实现者可以只关注这个函数的书写即可
     */
    public Flux<R> doProcess(Context context, Flux<T> monoDataFromLastPrc, Map<Integer, RunTimeContext> mapNewContext) {
        return Flux.empty();
    }

    /**
     * 获取上游的runTimeTask
     *
     * @param ctx Context
     * @return RunTimeContext
     */
    public final RunTimeContext getUpperRunTimeContext(Context ctx) {
        Map<Integer, RunTimeContext> runTimeContext = ctx.getOrDefault(upperStreamDataType(), new HashMap<>());
        return runTimeContext.getOrDefault(RuntimeConstant.RUNTIME_CTX_KEY, new RunTimeContext());
    }

    /**
     * 写下游context
     *
     * @param ctx               Context
     * @param runTimeContextMap Map<Integer, RunTimeContext>
     * @return Context
     */
    private Context putDownStreamRunTimeContext(Context ctx, Map<Integer, RunTimeContext> runTimeContextMap) {
        return ctx.put(downStreamDataType(), runTimeContextMap);
    }

    private String upperStreamDataType() {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        if (type instanceof Class) {
            return ((Class<R>) type).getSimpleName();
        } else {
            return ((Class) (((ParameterizedTypeImpl) type).getActualTypeArguments()[0])).getSimpleName();
        }
    }

    private String downStreamDataType() {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        if (type instanceof Class) {
            return ((Class<R>) type).getSimpleName();
        } else {
            return ((Class) (((ParameterizedTypeImpl) type).getActualTypeArguments()[0])).getSimpleName();
        }
    }
}