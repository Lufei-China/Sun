package com.inmobi.supply.sun.repo.redis.service;

import com.inmobi.supply.component.model.redis.MaterialSandbox;
import com.inmobi.supply.sun.repo.redis.BaseRepo;
import com.inmobi.supply.sun.repo.redis.impl.SandBoxRepoImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description SandBoxRepo
 * @Date 2022/10/24 10:51
 * @Author william.wang
 */
@Slf4j
@Repository
public class SandBoxRepo implements BaseRepo<Integer> {


    @Value("${sandBox.url.prefix:https://cdn-supply.inmobi.cn/}")
    private String prefix;

    @Autowired
    private SandBoxRepoImpl sandBoxRepoImpl;

    private volatile Map<String, List<MaterialSandbox>> materialSandboxMap;

    @Override
    public int size() {
        return materialSandboxMap.size();
    }

    @Override
    public Flux<Integer> loadRepo() {
        log.info("enter SandBoxRepo load");
        Map<String, List<MaterialSandbox>> newCache = new HashMap<>();
        return sandBoxRepoImpl.findAll()
                .switchIfEmpty(Flux.error(new Exception("no elements")))
                .filter(materialSandbox -> materialSandbox.getStatus() == 1
                        && ObjectUtils.isNotEmpty(materialSandbox.getAdType())
                        && ObjectUtils.isNotEmpty(materialSandbox.getOs())
                        && ObjectUtils.isNotEmpty(materialSandbox.getInteractionType())
                        && ObjectUtils.isNotEmpty(materialSandbox.getCreativeType())
                        && ObjectUtils.isNotEmpty(materialSandbox.getAspectRatio())
                )
                .flatMap(materialSandbox -> {
                    // url 加前缀
                    if (StringUtils.isNotEmpty(materialSandbox.getIconUrl())) {
                        materialSandbox.setIconUrl(prefix + materialSandbox.getIconUrl());
                    }
                    if (StringUtils.isNotEmpty(materialSandbox.getImageUrl())) {
                        materialSandbox.setImageUrl(prefix + materialSandbox.getImageUrl());
                    }
                    if (StringUtils.isNotEmpty(materialSandbox.getVideoUrl())) {
                        materialSandbox.setVideoUrl(prefix + materialSandbox.getVideoUrl());
                    }
                    if (StringUtils.isNotEmpty(materialSandbox.getCoverUrl())) {
                        materialSandbox.setCoverUrl(prefix + materialSandbox.getCoverUrl());
                    }
                    StringBuilder mockAdKey = new StringBuilder();
                    mockAdKey.append(materialSandbox.getAdType())
                            .append("_")
                            .append(materialSandbox.getOs())
                            /*.append("_")
                            .append(materialSandbox.getInteractionType())
                            .append("_")
                            .append(materialSandbox.getCreativeType())*/
                            .append("_")
                            .append(materialSandbox.getAspectRatio().replace(":", "_").replace("*", "_"));
                    ArrayList<MaterialSandbox> temp = new ArrayList<>();
                    temp.add(materialSandbox);
                    List<MaterialSandbox> materialSandboxes = newCache.putIfAbsent(mockAdKey.toString(), temp);
                    if (ObjectUtils.isNotEmpty(materialSandboxes)) {
                        newCache.get(mockAdKey.toString()).add(materialSandbox);
                        //newCache.put(mockAdKey.toString(), materialSandboxes);
                    }
                    return Flux.just(0);
                }).thenMany(Flux.just(0).flatMap(integer -> {
                    materialSandboxMap = newCache;
                    log.info("materialSandboxMap size={} {}", materialSandboxMap.size(), materialSandboxMap);
                    return Flux.just(0);
                })).onErrorResume(exception -> {
                    log.error("materialSandboxMap loadRepo error:" + exception);
                    return Flux.just(0);
                });
    }

    public MaterialSandbox getMockAdMap(String key, List<Integer> interactionTypeList, List<Integer> creativeTypeList) {
        List<MaterialSandbox> materialSandboxes = materialSandboxMap.get(key);
        List<MaterialSandbox> match = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(materialSandboxes) && materialSandboxes.size() > 0) {
            if (interactionTypeList.size() > 0 && creativeTypeList.size() > 0) {
                match = materialSandboxes.stream().filter(materialSandbox -> interactionTypeList.contains(materialSandbox.getInteractionType())
                        && creativeTypeList.contains(materialSandbox.getCreativeType())).collect(Collectors.toList());
            }
            if (match.size() == 0 && interactionTypeList.size() > 0) {
                match = materialSandboxes.stream().filter(materialSandbox -> interactionTypeList.contains(materialSandbox.getInteractionType())).collect(Collectors.toList());
            }
            if (match.size() == 0 && creativeTypeList.size() > 0) {
                match = materialSandboxes.stream().filter(materialSandbox -> creativeTypeList.contains(materialSandbox.getCreativeType())).collect(Collectors.toList());
            }
            if (match.size() == 0) {
                match = materialSandboxes;
            }
            return match.get((int) (Math.random() * materialSandboxes.size()));
        }
        return null;
    }
}
