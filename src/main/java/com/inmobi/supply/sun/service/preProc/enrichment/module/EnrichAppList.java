package com.inmobi.supply.sun.service.preProc.enrichment.module;

import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import cn.inmobi.pb.sun.rr.base.User;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.sun.model.appList.AppListInfo;
import com.inmobi.supply.sun.model.dataFlow.EnrichmentDataType;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.PublisherAppListRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EnrichAppList implements ModuleWorker<EnrichmentDataType> {

    @Autowired
    private PublisherAppListRepo publisherAppListRepo;

    @Override
    public Mono<EnrichmentDataType> processMono(RunTimeContext runTimeContext) {
        try {
            String accountId = runTimeContext.getPlacementModel().getAccountId();
            OsType osType = runTimeContext.getOsType();

            List<String> allTagIds = new ArrayList<>();
            //adapter 对应的 tagId
            List<String> baiduTagIdList = runTimeContext.getBaiduTagIdList();
            if (CollectionUtils.isNotEmpty(baiduTagIdList)) {
                allTagIds.addAll(baiduTagIdList);
            }
            BaseRequest baseRequest = runTimeContext.getBaseRequest();
            if (null != baseRequest && null != baseRequest.getUser()) {
                if (CollectionUtils.isNotEmpty(baseRequest.getUser().getTagIdList())) {
                    allTagIds.addAll(baseRequest.getUser().getTagIdList());
                }
            }
            if (CollectionUtils.isEmpty(allTagIds)) {
                return Mono.just(new EnrichmentDataType());
            }
            Map<String, List<AppListInfo>> publisherAppListInfoMap = publisherAppListRepo.getPublisherAppListInfoMap();
            if (MapUtils.isNotEmpty(publisherAppListInfoMap)) {
                List<AppListInfo> appListInfos = publisherAppListInfoMap.get(accountId.concat("_").concat(String.valueOf(osType.getNumber())));
                if (CollectionUtils.isNotEmpty(appListInfos)) {
                    LogUtils.logInfo(runTimeContext.getRequestId(),"appListInfos {}", JSON.toJSONString(appListInfos));
                    List<String> collect = appListInfos.stream().map(AppListInfo::getTagId).collect(Collectors.toList());
                    //集合取交集
                    List<String> intersection = allTagIds.stream().filter(collect::contains).collect(Collectors.toList());
                    List<AppListInfo> collectNew = appListInfos.stream().filter(appListInfo -> intersection.contains(appListInfo.getTagId())).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(collectNew)) {
                        List<String> collectNewAppList = collectNew.stream().map(AppListInfo::getBundleId).collect(Collectors.toList());
                        String appList = StringUtils.join(collectNewAppList, ",");
                        if (null != baseRequest.getUser() && StringUtils.isNotBlank(baseRequest.getUser().getAppList())) {
                            String appListOld = baseRequest.getUser().getAppList();
                            String allListNew = appListOld.concat(",").concat(appList);
                            User user = baseRequest.getUser();
                            user.toBuilder().setAppList(allListNew);
                            runTimeContext.setBaseRequest(baseRequest.toBuilder().setUser(user).build());
                        } else {
                            User.Builder builder = baseRequest.getUser().toBuilder();
                            builder.setAppList(appList);
                            runTimeContext.setBaseRequest(baseRequest.toBuilder().setUser(builder).build());
                        }
                        LogUtils.logInfo(runTimeContext.getRequestId(),"appListInfos newUser {}",runTimeContext.getBaseRequest().getUser());
                        PerfUtils.perf("sun.replace.appList.tagId");
                    }
                }
            }
        } catch (Exception e) {
            log.error("EnrichAppList error", e);
            return Mono.just(new EnrichmentDataType());
        }
        return Mono.just(new EnrichmentDataType());
    }
}
