package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;

import com.inmobi.supply.sun.constant.RequestType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description NormailzerRequestWorkerFactory
 * @Date 2022/5/9 11:46
 * @Author william.wang
 */
@Component
public class NormalizerRequestWorkerFactory implements InitializingBean, ApplicationContextAware {

    private final Map<RequestType, NormalizerRequestWorker> strategyMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() {
        applicationContext.getBeansOfType(NormalizerRequestWorker.class).values().forEach(worker -> worker.getRequestTypeList().forEach(requestType -> strategyMap.put(requestType, worker)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public NormalizerRequestWorker get(RequestType requestType) {
        return strategyMap.get(requestType);
    }


}
