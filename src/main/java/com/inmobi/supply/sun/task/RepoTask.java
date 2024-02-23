package com.inmobi.supply.sun.task;

import java.time.Duration;

import javax.annotation.PostConstruct;

import com.alibaba.fastjson.JSON;
import com.inmobi.supply.framework.common.utils.log.RateLogger;
import com.inmobi.supply.sun.repo.file.*;
import com.inmobi.supply.sun.repo.redis.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static com.inmobi.supply.sun.repo.file.UserGroupRepo.USER_GROUP_DELAY_SECONDS;
import static com.inmobi.supply.sun.repo.file.UserGroupRepo.USER_GROUP_INTERVAL_SECONDS;

@Service
@Slf4j
public class RepoTask {
    @Autowired
    private LanguageRepo languageRepo;

    @Autowired
    private DeviceRepo deviceRepo;

    @Autowired
    private PlacementRepo placementRepo;

    @Autowired
    private AppRepo appRepo;

    @Autowired
    private AppBundleOsRepo appBundleOsRepo;

    @Autowired
    private GeTuiProjectTaskRepo geTuiProjectTaskRepo;

    @Autowired
    private CreativeAuditRepo creativeAuditRepo;

    @Autowired
    private CnConfigRepo cnConfigRepo;

    @Autowired
    private CnConfigRedisRepo cnConfigRedisRepo;

    @Autowired
    private DynamicFloorRepo dynamicFloorRepo;

    @Autowired
    private SandBoxRepo sandBoxRepo;

    @Autowired
    private DeprecatedPlacementRepo deprecatedPlacementRepo;

    @Autowired
    private UserGroupRepo userGroupRepo;

    @Autowired
    private BlackListPlacementRepo blackListPlacementRepo;

    @Autowired
    NoviceRepo noviceRepo;

    @Autowired
    MixFlowRateRepo mixFlowRateRepo;

    @Autowired
    private UsdCnyRepo usdCnyRepo;

    @Autowired
    private MaterialInfoRepo materialInfoRepo;

    @Autowired
    private AppInfoBaseRepo appInfoBaseRepo;

    @Autowired
    private MixFlowBurnRepo mixFlowBurnRepo;

    @Autowired
    private PublisherAppListRepo publisherAppListRepo;

    @PostConstruct
    public void loadLanguage() {
        Flux.interval(languageRepo.delaySeconds(), languageRepo.intervalSeconds())
                .parallel().runOn(Schedulers.elastic()).flatMap(x -> {
            log.info("IntervalTask loadLanguage begin...");
            return languageRepo.loadRepo()
                    .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadLanguage interval task error occurred!!!", e));
        }).subscribe(p -> RateLogger.rateLogger(log).info("LanguageRepo snapshot map length : {}", languageRepo.size()));
    }

//    @PostConstruct
    public void loadDevice() {
        Flux.interval(deviceRepo.delaySeconds(), deviceRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask loadDevice begin...");
                    return deviceRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadDevice interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).debug("DeviceRepo snapshot map length : {}", deviceRepo.size()));
    }

    @PostConstruct
    public void loadPlacement() {
        Flux.interval(placementRepo.delaySeconds(), placementRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load placement begin...");
                    return placementRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadPlacement interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("PlacementRepo snapshot map length : {}", placementRepo.size()));
    }

    @PostConstruct
    public void loadApp() {
        Flux.interval(appRepo.delaySeconds(), appRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load app begin...");
                    return appRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadApp interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("AppRepo snapshot map length : {}", appRepo.size()));
    }

    @PostConstruct
    public void loadAppBundleOs() {
        Flux.interval(appBundleOsRepo.delaySeconds(), appRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadAppBundleOs begin...");
                    return appBundleOsRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadAppBundleOsRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("AppBundleOsRepo snapshot map length : {}", appBundleOsRepo.size()));
    }

    @PostConstruct
    public void loadProjectTaskId() {
        Flux.interval(geTuiProjectTaskRepo.delaySeconds(), geTuiProjectTaskRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load projectTaskId begin...");
                    return geTuiProjectTaskRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadProjectTaskId interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("demand project task id map {}", geTuiProjectTaskRepo.size()));
    }

    @PostConstruct
    public void loadCreativeAuditRes() {
        Flux.interval(creativeAuditRepo.delaySeconds(), creativeAuditRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load creativeAudit begin...");
                    return creativeAuditRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadCreativeAuditRes interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("creativeAudit load success : {}", creativeAuditRepo.size()));
    }

    @PostConstruct
    public void loadCnConfig() {
        Flux.interval(cnConfigRepo.delaySeconds(), cnConfigRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load cnConfig begin...");
                    return cnConfigRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadCnConfig interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("cnConfig load success : {}", cnConfigRepo.size()));
    }

    @PostConstruct
    public void loadMaterialInfoRepo() {
        Flux.interval(materialInfoRepo.delaySeconds(), materialInfoRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load materialInfoRepo begin...");
                    return materialInfoRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("materialInfoRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("materialInfoRepo load success : {}", materialInfoRepo.size()));
    }


    @PostConstruct
    public void loadCnConfigRedis() {
        Flux.interval(cnConfigRedisRepo.delaySeconds(), cnConfigRedisRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadCnConfigRedis begin...");
                    return cnConfigRedisRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadCnConfigRedis interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadCnConfigRedis load success : {}", cnConfigRepo.size()));
    }

    @PostConstruct
    public void loadDynamicFloorInfo() {
        Flux.interval(dynamicFloorRepo.delaySeconds(), dynamicFloorRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadDynamicFloorInfo begin...");
                    return dynamicFloorRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadDynamicFloorInfo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadDynamicFloorInfo load success : {}", dynamicFloorRepo.size()));
    }

    @PostConstruct
    public void loadSandBoxInfo() {
        Flux.interval(sandBoxRepo.delaySeconds(), sandBoxRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load SandBox begin...");
                    return sandBoxRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadSandBoxInfo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("SandBox load success : {}", sandBoxRepo.size()));
    }

    @PostConstruct
    public void loadDeprecatedPlacementInfo() {
        Flux.interval(deprecatedPlacementRepo.delaySeconds(), deprecatedPlacementRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load DeprecatedPlacementInfo begin...");
                    return deprecatedPlacementRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadDeprecatedPlacementRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadDeprecatedPlacementRepo load success : {}", deprecatedPlacementRepo.size()));
    }

    @PostConstruct
    public void loadBlackListPlacementInfo() {
        Flux.interval(blackListPlacementRepo.delaySeconds(), blackListPlacementRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadBlackListPlacementInfo begin...");
                    return blackListPlacementRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadBlackListPlacementRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadBlackListPlacementRepo load success : {}", blackListPlacementRepo.size()));
    }

    @PostConstruct
    public void loadBlockPlacement() {
        Flux.interval(Duration.ofSeconds(0), placementRepo.intervalSeconds())
                .parallel().runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadBlockPlacement begin...");
                    return placementRepo.loadBlockPlacement()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadBlockPlacement interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadBlockPlacement load success : {}", 0));
    }


    @PostConstruct
    public void loadUserGroup() {
        Flux.just(0).parallel().runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadUserGroup begin...");
                    return userGroupRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadUserGroup interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadUserGroup load success : {}", 0));
    }

    @PostConstruct
    public void loadNovice() {
        Flux.interval(noviceRepo.delaySeconds(), noviceRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadNoviceRepo begin...");
                    return noviceRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadNoviceRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadNoviceRepo load success : {}", 0));
    }

    @PostConstruct
    public void loadMixFlowRateInfo() {
        Flux.interval(mixFlowRateRepo.delaySeconds(), mixFlowRateRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load SandBox begin...");
                    return mixFlowRateRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadMixFlowRateInfo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadMixFlowRateInfo load success : {}", mixFlowRateRepo.size()));
    }

    @PostConstruct
    public void loadUsdCny() {
        Flux.interval(Duration.ofSeconds(0), usdCnyRepo.intervalSeconds())
                .parallel().runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("IntervalTask load loadUsdCny begin...");
                    return usdCnyRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("loadUsdCny interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("loadUsdCny load success : {}", 0));
    }

    @PostConstruct
    public void loadAppInfoBase() {
        Flux.interval(appInfoBaseRepo.delaySeconds(), appInfoBaseRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("appInfoBaseRepo load  begin...");
                    return appInfoBaseRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("appInfoBaseRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("appInfoBaseRepo load success : {}", appInfoBaseRepo.size()));
    }

    @PostConstruct
    public void mixFlowBurn() {
        Flux.interval(mixFlowBurnRepo.delaySeconds(), mixFlowBurnRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("mixFlowBurnRepo load  begin...");
                    return mixFlowBurnRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("mixFlowBurnRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("mixFlowBurnRepo load success : {}  map {}", mixFlowBurnRepo.getMixFlowTodayBurn(),mixFlowBurnRepo.getMixFlowTodayEvent()));
    }

    @PostConstruct
    public void publisherAppList() {
        Flux.interval(publisherAppListRepo.delaySeconds(), publisherAppListRepo.intervalSeconds())
                .parallel()
                .runOn(Schedulers.elastic())
                .flatMap(x -> {
                    log.info("publisherAppListRepo load  begin...");
                    return publisherAppListRepo.loadRepo()
                            .onErrorContinue((e, v) -> RateLogger.rateLogger(log).error("publisherAppListRepo interval task error occurred!!!", e));
                }).subscribe(p -> RateLogger.rateLogger(log).info("publisherAppListRepo load success publisherAppListRepoInfo {}", publisherAppListRepo.getPublisherAppListInfoMap()));
    }

}