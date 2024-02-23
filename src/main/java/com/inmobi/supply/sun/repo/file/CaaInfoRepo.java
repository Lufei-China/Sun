package com.inmobi.supply.sun.repo.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.geo.CaaGeoModel;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.utils.IpUtils;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * load caa info
 * @author remi.li
 */
@Slf4j
@Service
public class CaaInfoRepo implements BaseRepo<Object> {
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;
    @Getter
    private volatile List<CaaGeoModel> caaGeoModelList;

    @Override
    public int size() {
        return Optional.ofNullable(caaGeoModelList).orElse(new ArrayList<>()).size();
    }

    @Override
    public Flux<Object> loadRepo() {
        return Flux.just(0).flatMap(x -> {
            String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
            OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
            OSSObject caaIpdbMergedObject = ossClient.getObject(ossConfig.getBucketName(), "supply/caa/caa_ipdb_merged.txt");
            OSSObject caaCityProvinceObject = ossClient.getObject(ossConfig.getBucketName(), "supply/caa/caaCityProvince.csv");
            InputStream caaIpdbMergedStream = caaIpdbMergedObject.getObjectContent();
            InputStream caaCityProvinceInputStream = caaCityProvinceObject.getObjectContent();
            try {
                BufferedReader caaCityProvinceBr = new BufferedReader(new BufferedReader(new InputStreamReader(caaCityProvinceInputStream)));
                BufferedReader caaIpdbMergedBr = new BufferedReader(new BufferedReader(new InputStreamReader(caaIpdbMergedStream)));
                int i = 0;
                List<Map<String, String>> cityList = new ArrayList<>();
                String cityProvinceLine;
                while ((cityProvinceLine = caaCityProvinceBr.readLine()) != null) {
                    i++;
                    if (i == 1) {
                        continue;
                    }
                    String[] cityProvinceList =  StringUtils.split(cityProvinceLine, ",");
                    if (cityProvinceList.length < 3) {
                        continue;
                    }
                    String cityCode = cityProvinceList[0];
                    String cityName = cityProvinceList[1];
                    String stateName = cityProvinceList[2];
                    Map<String, String> map = new HashMap<>();
                    map.put("regionCode", cityCode);
                    map.put("city", cityName);
                    map.put("state", stateName);
                    cityList.add(map);
                }
                List<CaaGeoModel> tempCaaGeoModelList = new ArrayList<>();
                String caaIpLine;
                while ((caaIpLine = caaIpdbMergedBr.readLine()) != null) {
                    CaaGeoModel caaGeoModel = new CaaGeoModel();
                    String [] arr = StringUtils.split(caaIpLine, "\t");
                    String startIp = StringUtils.trim(arr[0]);
                    String endIp = StringUtils.trim(arr[1]);
                    String country = StringUtils.trim(arr[2]);
                    String district = StringUtils.trim(arr[3]);
                    String city = arr[4];
                    String regionCode = "";
                    String cityName = "";
                    String stateName = "";
                    if(city.equals("*")&&country.equals("中国")){
                        city=district;
                    }
                    for (Map<String, String> cityMap : cityList) {
                        if (StringUtils.isNotBlank(cityName)) {
                            continue;
                        }
                        if (city.equals("*") && cityMap.get("city").contains(district) && cityMap.get("state").contains(country)) {
                            regionCode = cityMap.get("regionCode");
                            cityName = cityMap.get("city");
                            stateName = cityMap.get("state");

                        } else if (cityMap.get("city").contains(city) && cityMap.get("state").contains(district)) {
                            regionCode = cityMap.get("regionCode");
                            cityName = cityMap.get("city");
                            stateName = cityMap.get("state");
                        }
                        if (StringUtils.isBlank(cityName) && district.equals(country) && city.equals("*")) {
                            if (cityMap.get("city").equals(district) && cityMap.get("state").equals("全球")) {
                                regionCode = cityMap.get("regionCode");
                                cityName = cityMap.get("city");
                                stateName = cityMap.get("state");
                            }
                        }
                    }
                    if (StringUtils.isBlank(cityName)) {
                        for (Map<String, String> cityMap : cityList) {
                            if (cityMap.get("city").contains(district)) {
                                regionCode = cityMap.get("regionCode");
                                cityName = cityMap.get("city");
                                //stateName = cityMap.get("state");
                                stateName = cityMap.get("city");
                                break;
                            } else if (cityMap.get("city").contains(city)) {
                                regionCode = cityMap.get("regionCode");
                                cityName = cityMap.get("city");
                                stateName = cityMap.get("state");
                                break;
                            }  else if (cityMap.get("city").contains(country)) {
                                regionCode = cityMap.get("regionCode");
                                cityName = cityMap.get("city");
                                stateName = cityMap.get("state");
                                break;
                            }
                        }
                    }
                    if (StringUtils.isBlank(cityName)) {
                        continue;
                    }
                    String cityCode = regionCode.substring(4);
                    String provinceCode = String.valueOf(cityCode.charAt(0)) + String.valueOf(cityCode.charAt(1)) + "0000";
                    caaGeoModel.setIpRangeFrom(startIp);
                    caaGeoModel.setIpRangeEnd(endIp);
                    caaGeoModel.setIntRangeFrom(IpUtils.ipv4ToLong(startIp));
                    caaGeoModel.setIntRangeEnd(IpUtils.ipv4ToLong(endIp));
                    caaGeoModel.setCaaCityName(cityName);
                    if("中国".equals(stateName) || stateName.contains("中国")){
                        //对包含中国的省份进行替换
                        caaGeoModel.setCaaStateName(cityName);
                    }else {
                        caaGeoModel.setCaaStateName(stateName);
                    }
                    caaGeoModel.setCaaCityCode(cityCode);
                    caaGeoModel.setCaaProvinceCode(provinceCode);
                    tempCaaGeoModelList.add(caaGeoModel);
                }
                caaGeoModelList = tempCaaGeoModelList;
            } catch (Exception e) {
                log.error("CaaIpRepo process error", e);
            }
            return Flux.just(0);
        });
    }
}
