package com.inmobi.supply.sun.repo.file;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.inmobi.pb.api.OsType;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.framework.common.utils.log.RateLogger;
import com.inmobi.supply.sun.config.OssConfig;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import javax.annotation.PostConstruct;

/**
 * 自有人群包
 * 不继承BaseRepo，加载时间过长，并且不用在run起来之前加载
 *
 * @author remi.li
 */
@Slf4j
@Service
public class UserGroupRepo {
    public static long USER_GROUP_DELAY_SECONDS = 30L;
    public static long USER_GROUP_INTERVAL_SECONDS = 43200L;
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    private static List<UserGroupData> filePathList = new ArrayList<>();

    @Getter
    private volatile Map<String, Tuple3<BloomFilter<String>, String, OsType>> bloomFilterMap;

    static {
        filePathList.add(UserGroupData.of("supply/userGroup/FL-LX-A001.csv", 23774994L, "10000553", OsType.ANDROID));
        //filePathList.add(UserGroupData.of("supply/userGroup/FL-LX-A002.csv", 46261632L, "10000554", OsType.ANDROID));
        //filePathList.add(UserGroupData.of("supply/userGroup/FL-LX-A003.csv", 52207069L, "10000555", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/2266.txt", 46006930L, "10000643", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/2264_00.txt", 54017551L, "10000645", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/2264_01.txt", 24244359L, "10000645", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/2263_00.txt", 54017551L, "10000644", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/2263_01.txt", 33207337L, "10000644", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/mtwm_idfa.csv", 9418994L, "10000734", OsType.IOS));
        filePathList.add(UserGroupData.of("supply/userGroup/mtwm_imei_md5.csv", 12322859L, "10000733", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/imei1.csv", 130150524L, "10000800", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/imei2.csv", 130150525L, "10000800", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/imei3.csv", 113010646L, "10000800", OsType.ANDROID));
        filePathList.add(UserGroupData.of("supply/userGroup/ios1.csv", 116080197L, "10000900", OsType.IOS));
        filePathList.add(UserGroupData.of("supply/userGroup/ios2.csv", 58662896L, "10000900", OsType.IOS));


    }

    public Flux<Integer> loadRepo() {
        String ossConfigStr = dynamicConfigService.getConfig("application").getProperty("oss.config", "");
        OssConfig ossConfig = ObjectMapperUtils.fromJSON(ossConfigStr, OssConfig.class);
        Map<String, Tuple3<BloomFilter<String>, String, OsType>> tempBloomFilterMap = new HashMap<>();
        return Flux.fromIterable(filePathList).flatMap(userGroupData -> {
            BloomFilter<String> filter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8), userGroupData.getRecordCount(), 0.005);

            OSSObject object = ossClient.getObject(ossConfig.getBucketName(), userGroupData.getPath());
            InputStream stream = object.getObjectContent();
            BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(stream)));
            String line = "";
            try {
                while ((line = br.readLine()) != null) {
                    if (StringUtils.isNotEmpty(line)) {
                        filter.put(line);
                    }
                }
                log.info("BloomFilter {} init success, count={}", userGroupData.getPath(), filter.approximateElementCount());
            } catch (Throwable e) {
                log.error("BloomFilter {} error {} line {}", userGroupData.getPath(), e, line);
            }
            tempBloomFilterMap.put(userGroupData.getPath(), Tuples.of(filter, userGroupData.getCsid(), userGroupData.getOsType()));
            return null;
        }).thenMany(Flux.just(0).flatMap(integer -> {
            bloomFilterMap = tempBloomFilterMap;
            return Flux.just(0);
        })).onErrorResume(exception -> {
            log.error("UserGroupRepo loadRepo error:" + exception.getMessage());
            return Flux.just(0);
        });
    }

    @Data
    @AllArgsConstructor
    public static class UserGroupData {
        private String path;
        private long recordCount;
        private String csid;
        private OsType osType;

        public static UserGroupData of(String path, long recordCount, String csid, OsType osType) {
            return new UserGroupData(path, recordCount, csid, osType);
        }
    }
}
