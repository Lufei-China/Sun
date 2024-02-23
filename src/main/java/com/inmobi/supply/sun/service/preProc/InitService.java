package com.inmobi.supply.sun.service.preProc;

import java.util.HashMap;
import java.util.Map;

import cn.inmobi.pb.sun.rr.OriginalRequest;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.preProc.normalizer.NormalizerService;
import com.inmobi.supply.sun.utils.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.util.context.Context;

/**
 *
 * 初始化服务
 *
 * @author remi.li
 */
@Slf4j
@Service
public class InitService {
    @Autowired
    private NormalizerService normalizerService;

    /**
     * 初始化sun上下文对象
     * @param context 上下文
     * @param serverRequest ServerRequest
     * @return Context 新建上下文
     */
    public Context initContext(Context context, ServerRequest serverRequest) {
        RunTimeContext runTimeContext = new RunTimeContext();
        Map<Integer, RunTimeContext> runTimeContextMap = new HashMap<>();
        //init runTimeContext
        runTimeContext.setServerRequest(serverRequest);
        runTimeContext.setStartTime(System.currentTimeMillis());
        runTimeContext.setAsyncServletResponse(new AsyncServletResponse());
        runTimeContext.setRequestId(IdGenerator.genRequestId());
        runTimeContext.setOriginalRequestBuilder(OriginalRequest.newBuilder());
        runTimeContextMap.put(RuntimeConstant.RUNTIME_CTX_KEY, runTimeContext);
        context = context.put(RuntimeConstant.FIRST_PROC_CTX_KEY, runTimeContextMap);
        return context;
    }

    public Worker getStartModule() {
        return normalizerService;
    }
}