package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;

import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;

import java.util.List;

/**
 * @Description AbstractNormalizerRequest
 * @Date 2022/5/7 16:54
 * @Author william.wang
 */
public interface NormalizerRequestWorker {


    /**
     * parseRequest
     *
     * @param runTimeContext
     * @throws SunException
     */
    void parseRequest(RunTimeContext runTimeContext) throws SunException;

    /**
     * getRequestType  一个worker可能被多个RequestType使用
     * @return
     */
    List<RequestType> getRequestTypeList();


}
