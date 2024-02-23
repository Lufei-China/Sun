package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 分时间段/出价区间/小时的placementId表现情况，比如ctr/cpm/cpc等
 *
 * @author remi.li
 */
@Slf4j
@Service
@SuppressWarnings("unchecked")
public class PlacementMetricsRepo implements BaseRepo<Object> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    /**
     * <pm_placementId_priceLocation, AvgMetrics>
     */
    @Getter
    private volatile Map<String, Map<String, Double>> placementPriceAvgMetricsMap;

    /**
     * <pm_placementId_priceLocation_hour, AvgMetrics>
     */
    @Getter
    private volatile Map<String, Map<String, Double>> placementHourPriceAvgMetricsMap;

    /**
     * <pm_hour_priceLocation, AvgMetrics>
     */
    @Getter
    private volatile Map<String, Map<String, Double>> hourPriceAvgMetricsMap;

    @Override
    public Flux<Object> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            /*String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);

            OSSObject placementHourPriceMetricsObject = ossClient.getObject(ossConfig.getBucketName(), "predict-server/placementMetrics/placementHourPriceFile.txt");
            InputStream placementHourPriceMetricsInputStream = placementHourPriceMetricsObject.getObjectContent();

            OSSObject placementPriceMetricsObject = ossClient.getObject(ossConfig.getBucketName(), "predict-server/placementMetrics/placementPriceFile.txt");
            InputStream placementPriceInputStream = placementPriceMetricsObject.getObjectContent();

            OSSObject hourPriceMetricsObject = ossClient.getObject(ossConfig.getBucketName(), "predict-server/placementMetrics/hourPriceFile.txt");
            InputStream hourPriceMetricsInputStream = hourPriceMetricsObject.getObjectContent();

            Map<String, Map<String, Double>> tempPlacementPriceAvgMetricsMap = new HashMap<>();
            Map<String, Map<String, Double>> tempPlacementHourPriceMetricsMap = new HashMap<>();
            Map<String, Map<String, Double>> tempHourPriceMetricsMap = new HashMap<>();

            try {
                BufferedReader placementHourPriceAvgBr = new BufferedReader(new BufferedReader(new InputStreamReader(placementHourPriceMetricsInputStream)));
                String placementHourAvgLine = placementHourPriceAvgBr.readLine();
                tempPlacementHourPriceMetricsMap = ObjectMapperUtils.fromJSON(placementHourAvgLine, Map.class);

                BufferedReader placementPriceAvgBr = new BufferedReader(new BufferedReader(new InputStreamReader(placementPriceInputStream)));
                String placmentPriceAvgline = placementPriceAvgBr.readLine();
                tempPlacementPriceAvgMetricsMap = ObjectMapperUtils.fromJSON(placmentPriceAvgline, Map.class);


                BufferedReader hourPriceAvgBr = new BufferedReader(new BufferedReader(new InputStreamReader(hourPriceMetricsInputStream)));
                String hourAvgLine = hourPriceAvgBr.readLine();
                tempHourPriceMetricsMap = ObjectMapperUtils.fromJSON(hourAvgLine, Map.class);
            } catch (Exception e) {
                log.error("placementMetricsRepo process error", e);
            }
            if (MapUtils.isNotEmpty(tempPlacementPriceAvgMetricsMap)) {
                placementPriceAvgMetricsMap = tempPlacementPriceAvgMetricsMap;
            }

            if (MapUtils.isNotEmpty(tempPlacementHourPriceMetricsMap)) {
                placementHourPriceAvgMetricsMap = tempPlacementHourPriceMetricsMap;
            }

            if (MapUtils.isNotEmpty(tempHourPriceMetricsMap)) {
                hourPriceAvgMetricsMap = tempHourPriceMetricsMap;
            }*/
            return Flux.just(0);
        });
    }
}