package com.inmobi.supply.sun.repo.file;

import com.alibaba.fastjson.JSONObject;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.config.dynamic.DynamicStringConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.HttpClientUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Service
public class UsdCnyRepo implements BaseRepo<Integer> {

    @Autowired
    DynamicConfigService dynamicConfigService;

    @Getter
    private BigDecimal usdCny;

    @Override
    public Flux<Integer> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            String url = dynamicConfigService.getConfig("application").getProperty("usd.cny.url", "http://webforex.hermes.hexun.com/forex/quotelist?code=FOREXUSDCNY&column=Code,Price");
            String value = HttpClientUtils.methodGet(url);
            JSONObject jsonObject = JSONObject.parseObject(
                    value.replace("(", "")
                            .replace(")", "")
                            .replace(";", ""));
            Object data = jsonObject.getJSONArray("Data").getJSONArray(0).getJSONArray(0).get(1);
            usdCny = new BigDecimal(String.valueOf(data)).divide(new BigDecimal("10000"), 4, BigDecimal.ROUND_HALF_UP);
            log.info("usdToCnyUnit {}",usdCny);
            //美元范围4到10人民币
            if(null == usdCny || usdCny.compareTo(BigDecimal.valueOf(4)) <= 0 || usdCny.compareTo(BigDecimal.valueOf(10)) >= 0){
                return Flux.error(new SunException(TermCode.SERVER_ERROR, new RunTimeContext()));
            }
            return Flux.just(0);
        }).onErrorResume(throwable -> {
            log.error("load UsdCnyRepo error!", throwable);
            return Flux.just(0);
        });
    }

    /**
     * 任务间隔执行秒数
     *
     * @return
     */
    @Override
    public Duration intervalSeconds() {
        return Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS*2);
    }
}
