package com.inmobi.supply.sun.service.preProc;

import cn.inmobi.pb.sun.rr.OriginalRequest;
import com.inmobi.supply.sun.constant.RuntimeConstant;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.service.preProc.normalizer.Normalizer2Service;
import com.inmobi.supply.sun.service.preProc.normalizer.NormalizerService;
import com.inmobi.supply.sun.utils.IdGenerator;
import com.inmobi.supply.sun.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.util.context.Context;

/**
 * 功能概述
 * className:      init2Service
 * package:        com.inmobi.supply.sun.service
 * author:         Gavin.Xu
 * date:           2022/3/16
 */
@Service
public class Init2Service {
    @Autowired
    private Normalizer2Service normalizer2Service;

    /**
     * 初始化sun上下文对象
     * @param context 上下文
     * @param serverRequest ServerRequest
     * @return Context 新建上下文
     */
    public Context initContext(Context context, ServerRequest serverRequest) {
        RunTimeContext runTimeContext = new RunTimeContext();
        //init runTimeContext
        runTimeContext.setServerRequest(serverRequest);
        runTimeContext.setStartTime(System.currentTimeMillis());
        runTimeContext.setAsyncServletResponse(new AsyncServletResponse());
        runTimeContext.setRequestId(IdGenerator.genRequestId());
        LogUtils.logInfo(runTimeContext.getRequestId(),"first log");
        context = context.put(Worker2.CONTEXT_KEY, runTimeContext);
        return context;
    }

    public Worker2 getStartModule() {
        return normalizer2Service;
    }
}
