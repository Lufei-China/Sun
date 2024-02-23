package com.inmobi.supply.sun.config;

import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.model.runtime.IosModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ios设备型号加载
 *
 * @author remi.li
 */
@Slf4j
@Configuration
public class IosModelConfig {

    @Bean
    public Map<String, IosModel> getIosModel() {
        InputStream in = getClass().getResourceAsStream("/handset/ios.json");
        Map<String, IosModel> iosModelMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(in)))) {
            String line;
            while ((line = br.readLine()) != null) {
                List<IosModel> iosModelList = ObjectMapperUtils.fromJSONArray(line, IosModel.class);
                if (CollectionUtils.isNotEmpty(iosModelList)) {
                    iosModelList.forEach(model -> {
                        if (model != null && StringUtils.isNotBlank(model.getModel())) {
                            iosModelMap.putIfAbsent(StringUtils.trim(StringUtils.lowerCase(model.getModel())), model);
                        }
                    });
                }
            }
            log.info("IosModel size={}", iosModelMap.size());
        }  catch (Exception e) {
            log.error("IosModel load error", e);
        }
        return iosModelMap;
    }
}
