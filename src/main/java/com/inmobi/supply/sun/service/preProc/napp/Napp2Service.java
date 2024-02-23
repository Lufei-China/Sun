package com.inmobi.supply.sun.service.preProc.napp;

import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.api.ConnectionType;
import cn.inmobi.pb.api.IntegrationType;
import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.earth.ReactorEarthServiceGrpc;
import cn.inmobi.pb.earth.entity.*;
import cn.inmobi.pb.sun.rr.SunRR;
import com.alibaba.fastjson.JSON;
import com.framework.starter.grpc.client.GrpcClient;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.ABTestConfig;
import com.inmobi.supply.sun.config.Experiment;
import com.inmobi.supply.sun.config.ExperimentGroup;
import com.inmobi.supply.sun.config.dynamic.DynamicBooleanConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicJsonConfig;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.constant.*;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.dataFlow.NappDataType;
import com.inmobi.supply.sun.model.dataFlow.NoviceDataType;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.Worker2;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import com.inmobi.supply.sun.utils.RandomUtils;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static com.inmobi.supply.component.constant.TermCode.DROP_BY_NAPP;
import static com.inmobi.supply.sun.config.dynamic.DynamicMapConfig.ID_CONFIDENCE_PLACEMENT;


/**
 * napp反作弊模块
 * 一期暂无
 *
 * @date 20200512
 */

@Service
@Slf4j
public class Napp2Service extends Worker2<NoviceDataType, AsyncServletResponse> {
    private static int DROP_SCORE = 10;

    @Autowired
    private Worker2<NappDataType,AsyncServletResponse> nextWorker;

    @GrpcClient(name = "earth")
    private ReactorEarthServiceGrpc.ReactorEarthServiceStub earthServiceStub;


    @Override
    protected Mono<? extends AsyncServletResponse> apply(NoviceDataType noviceDataType) {
        return Mono.deferContextual(Mono::just).flatMap(context -> {
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            if (ifRunWorker(runTimeContext)) {
                PerfUtils.perf(PerfConstant.ENTER_SERVICE_COUNT, PerfConstant.ENTER_SERVICE_TAG, "NappService");
                /**
                 * 记录经过反作弊，要记录全量日志，改动需慎重
                 */
                runTimeContext.setExecuteAntiFraud(true);
                //默认response
                runTimeContext.setEarthResponse(EarthResponse.newBuilder().setScore(90).setTencentScore(0).build());
                // header(调试) or SDK 流量均可以跳过反作弊
                boolean ifSkipNapp = SymbolString.TRUE.equalsIgnoreCase(runTimeContext.getHeaders().get(StringUtils.lowerCase(RequestHeader.NO_VALIDATE_HEADER)))
                        || runTimeContext.getRequestType() == RequestType.SDK;

                /**
                 * 指定 bundle 和 广告位 不过反作弊 BRUSH_FLOW_PLACEMENT_LIST
                 */
                if (DynamicListConfig.BRUSH_FLOW_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())
                        && DynamicListConfig.BRUSH_FLOW_BUNDLE_LIST.get().contains(runTimeContext.getRequestBundleId())) {
                    PerfUtils.perf("sun.brush.napp");
                    return Mono.just(new NappDataType());
                }

                if (DynamicListConfig.WHITE_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())) {
                    PerfUtils.perf("sun.white.placement","type","napp");
                    return Mono.just(new NappDataType());
                }


                /**
                 * 测试设备，测试广告位，测试header不过反作弊？会不会有漏洞？
                 */
                if (ifSkipNapp || Optional.ofNullable(runTimeContext.getTestTraffic()).orElse(false)) {
                    return Mono.just(new NappDataType());
                }
                if (DynamicListConfig.NAPP_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())) {
                    runTimeContext.setNeedTraceApi(true);
                }
                Boolean enabled = DynamicBooleanConfig.EARTH_ENABLED.get();
                if (enabled) {
                    Mono<EarthRequest> earthRequestMono;
                    try {
                        idConfidenceAbTest(runTimeContext);
                        earthRequestMono = buildEarthRequest(runTimeContext);
                    } catch (Exception e) {
                        log.error("napp service error!", e);
                        return Mono.just(new NappDataType());
                    }
                    return earthRequestMono
                            .flatMap(request -> earthServiceStub.scoreRequest(request)
                                    .timeout(Duration.ofMillis(20))
                                    .onErrorResume(throwable -> {
                                        if (throwable instanceof TimeoutException
                                                || throwable instanceof InterruptedException
                                                || throwable instanceof StatusRuntimeException) {
                                            PerfUtils.perf(PerfConstant.EARTH_REQUEST, Tag.of(PerfConstant.EARTH_REQUEST_STATUS_TAG, "timeout"));
                                        } else {
                                            PerfUtils.perf(PerfConstant.EARTH_REQUEST, Tag.of(PerfConstant.EARTH_REQUEST_STATUS_TAG, "error"));
                                        }
                                        return Mono.just(EarthResponse.newBuilder().setScore(90).setTencentScore(0).build());
                                    })).defaultIfEmpty(EarthResponse.newBuilder().setScore(90).setTencentScore(0).build())
                            .flatMap(response -> {
                                if (CollectionUtils.isNotEmpty(response.getExperimentsList())) {
                                    runTimeContext.getExperimentFlags().addAll(response.getExperimentsList());
                                }

                                if (CollectionUtils.isNotEmpty(response.getBidExperimentsList())) {
                                    runTimeContext.setNappExperiments(response.getBidExperimentsList());
                                }
                                runTimeContext.setEarthResponse(response);
                                if (response.getScore() <= DROP_SCORE) {
                                    LogUtils.logInfo(runTimeContext.getRequestId(),"Drop by Napp {}", response);
                                    return Mono.error(new SunException(DROP_BY_NAPP, runTimeContext));
                                } else {
                                    return Mono.just(new NappDataType());
                                }
                            });
                } else {
                    return Mono.just(new NappDataType());
                }
            }
            return Mono.just(new NappDataType());
        }).onErrorResume(throwable -> Mono.deferContextual(Mono::just).flatMap(context -> {

            //异常处理流程
            RunTimeContext runTimeContext = context.get(CONTEXT_KEY);
            runTimeContext.setThrowable(throwable);
            runTimeContext.setError(true);
            LogUtils.logError(runTimeContext.getRequestId(),"Napp unknown error {}", throwable);
            return Mono.just(new NappDataType());
        })).transform(nextWorker.transform());
    }


    private Mono<EarthRequest> buildEarthRequest(RunTimeContext runTimeContext) {
        EarthRequest.Builder builder = EarthRequest.newBuilder();
        builder.setRequestId(runTimeContext.getRequestId());
        builder.setEventType(1);
        Device.IdType priorityDeviceId = getPriorityDeviceId(runTimeContext);
        if(null != priorityDeviceId){
            builder.setDeviceId(priorityDeviceId.getDeviceId());
            builder.setDeviceIdType(priorityDeviceId.getDeviceIdType());
        }
        builder.setOsType(runTimeContext.getDevice().getOsType().getNumber());
        builder.setId(getDeviceId(runTimeContext));
        SupplySource supplySource = getSupplySource(runTimeContext);
        NetworkingInfo networkingInfo = getNetworkInfo(runTimeContext);
        RequestDeviceInfo requestDeviceInfo = getDeviceInfo(runTimeContext);
        builder.putAllAbTestTags(runTimeContext.getAbTestTags());
        return Mono.just(builder.
                setNetworkingInfo(networkingInfo).
                setDeviceInfo(requestDeviceInfo).
                setSupplySource(supplySource).build());
    }

    private Id getDeviceId(RunTimeContext runTimeContext) {
        Id.Builder builder = Id.newBuilder();
        Device.IdMapping id = runTimeContext.getDevice().getId();
        builder.setIdfa(Optional.ofNullable(id.getIdfa()).orElse(""));
        builder.setIdfv(Optional.ofNullable(id.getIdfv()).orElse(""));
        builder.setImeiMd5(Optional.ofNullable(id.getImeiMd5()).orElse(""));
        builder.setImeiSha1(Optional.ofNullable(id.getImeiSha1()).orElse(""));
        builder.setImei(Optional.ofNullable(id.getImei()).orElse(""));
        builder.setOaid(Optional.ofNullable(id.getOaid()).orElse(""));
        builder.setAndroidMd5(Optional.ofNullable(id.getUm5()).orElse(""));
        builder.setAndroidSha1(Optional.ofNullable(id.getO1()).orElse(""));
        builder.setAndroidId(Optional.ofNullable(id.getAndroidId()).orElse(""));
        builder.setIdfaMd5(Optional.ofNullable(id.getIdfaMd5()).orElse(""));
        builder.setOaidMd5(Optional.ofNullable(id.getOaidMd5()).orElse(""));
        return builder.build();
    }

    private Device.IdType getPriorityDeviceId(RunTimeContext runTimeContext) {
        Device device = runTimeContext.getDevice();
        Device.IdType idType;
        if (device.getOsType() == OsType.IOS) {
            idType = device.getId().getPriorityIosId();
        } else {
            idType = device.getId().getPriorityAndId();
        }
        return idType;
    }

    private RequestDeviceInfo getDeviceInfo(RunTimeContext runTimeContext) {
        RequestDeviceInfo.Builder builder = RequestDeviceInfo.newBuilder();
        builder.setOsType(Optional.ofNullable(runTimeContext.getDevice().getOsType()).orElse(OsType.UNKNOW_OS_TYPE));
        builder.setUserAgent(Optional.ofNullable(runTimeContext.getUserAgent()).orElse(""));
        builder.setModel(Optional.ofNullable(runTimeContext.getDevice().getModel()).orElse(""));
        //osv
        builder.setOsv(Optional.ofNullable(runTimeContext.getDevice().getOsVersion()).orElse(""));
        //brand
        builder.setBrand(Optional.ofNullable(runTimeContext.getDevice().getVendor()).orElse(""));
        //判断Device为空的情况防止空指针异常
        if (runTimeContext.getDevice().getDeviceSize() != null) {
            builder.setWidth(runTimeContext.getDevice().getDeviceSize().getWidth());
            builder.setHeight(runTimeContext.getDevice().getDeviceSize().getHeight());
        } else {
            builder.setWidth(0);
            builder.setHeight(0);
        }
        return builder.build();

    }

    private NetworkingInfo getNetworkInfo(RunTimeContext runTimeContext) {
        NetworkingInfo.Builder builder = NetworkingInfo.newBuilder();
        builder.setRequestIPv4(Optional.ofNullable(runTimeContext.getUserIpV4()).orElse(""));
        builder.setRequestIPv6(Optional.ofNullable(runTimeContext.getUserIpV6()).orElse(""));
        builder.setHeadersJson(ObjectMapperUtils.toJSON(runTimeContext.getHeaders()));
        builder.setConnectionType(Optional.ofNullable(runTimeContext.getDevice().getConnectType()).orElse(ConnectionType.UNKNOW_CONNECTION_TYPE));
        builder.setCarrier(0);
        if (runTimeContext.getDevice() != null && runTimeContext.getDevice().getCarrier() != null) {
            builder.setCarrier(runTimeContext.getDevice().getCarrier().getNumber());
        }
        return builder.build();
    }

    private SupplySource getSupplySource(RunTimeContext runTimeContext) {
        SupplySource.Builder builder = SupplySource.newBuilder();
        builder.setPlacementId(Optional.ofNullable(runTimeContext.getPlacementModel().getId()).orElse(0L));
        builder.setAdType(Optional.ofNullable(runTimeContext.getAdType()).orElse(AdType.UNKNOW_AD_TYPE));
        builder.setAppId(null != runTimeContext.getPlacementModel() ? Optional.ofNullable(runTimeContext.getPlacementModel().getAppId()).orElse("") : "");
        builder.setBundleId(null != runTimeContext.getAppModel() ? Optional.ofNullable(runTimeContext.getAppModel().getBundleId()).orElse("") : runTimeContext.getRequestBundleId());
        builder.setAccountId(Optional.ofNullable(runTimeContext.getPlacementModel().getAccountId()).orElse(""));
        builder.setIntegrationType(runTimeContext.getRequestType() == RequestType.SDK ? IntegrationType.SDK : IntegrationType.API);
        builder.setIntegrationVersion(StringUtils.isBlank(runTimeContext.getSdkVersion()) ? "0.0.0" : runTimeContext.getSdkVersion());
        builder.setAppName(null != runTimeContext.getAppModel() ? Optional.ofNullable(runTimeContext.getAppModel().getAppName()).orElse(""):"");
        if (runTimeContext.getLat() == LatType.AUTHORIZED.getValue()) {
            builder.setIsLat(false);
        } else {
            builder.setIsLat(true);
        }
        return builder.build();
    }


    /**
     * ID_CONFIDENCE_AB_TEST
     * ID置信度实验参数处理
     * 针对安卓设备，osv 版本10及以上 只有oaid 或 oaidMd5 参数才走实验，10以下 有imei 或 imeiMd5 就可以走实验
     *
     * @param runTimeContext
     */
    private void idConfidenceAbTest(RunTimeContext runTimeContext) {
        PerfUtils.perf("sun.napp.idConfidence.request", "status", "request");
        Map<?, ?> map = ID_CONFIDENCE_PLACEMENT.get();
        String placementName = (String) map.get(String.valueOf(runTimeContext.getPlacementId()));
        LogUtils.logInfo(runTimeContext.getRequestId(),"idConfidenceAbTest PlacementNameMap {}, placementName {}", JSON.toJSONString(map),placementName);
        if(StringUtils.isBlank(placementName)){
            return;
        }
        ABTestConfig abTestConfig = null;
        try {
            abTestConfig = DynamicJsonConfig.AB_TEST_CONFIG.get();
            LogUtils.logInfo(runTimeContext.getRequestId(), "ABTestConfig info {}, Layers {}", JSON.toJSONString(abTestConfig), Objects.isNull(abTestConfig.getLayers()) ? "" : JSON.toJSONString(abTestConfig.getLayers()));
        } catch (Exception e) {
            LogUtils.logError(runTimeContext.getRequestId(), "abTestConfigErrorInfo {}", e);
        }
        if (null == abTestConfig) {
            return;
        }
        PerfUtils.perf("sun.napp.idConfidence.request", "status", "all");
        try {
            boolean abTestFlag = false;
            LogUtils.logInfo(runTimeContext.getRequestId(),"idConfidenceInfo osType {},osv {}",runTimeContext.getDevice().getOsType(),runTimeContext.getDevice().getOsVersion());
            if (OsType.ANDROID.equals(runTimeContext.getDevice().getOsType())) {
                PerfUtils.perf("sun.napp.idConfidence.test", Tag.of("type", "android"),Tag.of("placement",String.valueOf(runTimeContext.getPlacementId())));
                String osVersion = runTimeContext.getDevice().getOsVersion();
                LogUtils.logInfo("idConfidence osv {}", osVersion);
                String imei = runTimeContext.getDevice().getId().getImei();
                String imeiMd5 = runTimeContext.getDevice().getId().getImeiMd5();
                String oaid = runTimeContext.getDevice().getId().getOaid();
                String oaidMd5 = runTimeContext.getDevice().getId().getOaidMd5();
                if (StringUtils.isNotBlank(osVersion) && Integer.parseInt(StringUtils.split(osVersion, ".")[0]) >= 10) {
                    //安卓版本10及以上是否只有oaid 或者 oaidMd5 没有 imei 相关参数
                    if ((StringUtils.isBlank(imei) && StringUtils.isBlank(imeiMd5)) && (StringUtils.isNotBlank(oaid) || StringUtils.isNotBlank(oaidMd5))) {
                        //可以走测试规则
                        abTestFlag = true;
                        PerfUtils.perf("sun.napp.idConfidence.test", Tag.of("type", "androidTest"),Tag.of("placement",String.valueOf(runTimeContext.getPlacementId())));
                    }
                } else if (StringUtils.isNotBlank(osVersion) && Integer.parseInt(StringUtils.split(osVersion, ".")[0]) < 10) {
                    //包含imei 或者 imeiMd5 就可以
                    if (StringUtils.isNotBlank(imei) || StringUtils.isNotBlank(imeiMd5)) {
                        abTestFlag = true;
                        PerfUtils.perf("sun.napp.idConfidence.test", Tag.of("type", "androidTest"),Tag.of("placement",String.valueOf(runTimeContext.getPlacementId())));
                    }
                }
            } else {
                abTestFlag = true;
                PerfUtils.perf("sun.napp.idConfidence.test", Tag.of("type", "iosTest"),Tag.of("placement",String.valueOf(runTimeContext.getPlacementId())));
            }

            if (abTestFlag) {
                PerfUtils.perf("sun.napp.idConfidence.test", Tag.of("type", "success"),Tag.of("placement",String.valueOf(runTimeContext.getPlacementId())));
                //获取反作弊ID置信度实验参数，流量拆分(至少两个桶，可能大于等于二，尽可能保证均匀落到每个桶)，判断是否需要请求ID置信度服务
                Map<String, Experiment> layers = abTestConfig.getLayers();
                Experiment experiment = layers.get("anti-fraud");
                //总的实验规则  流量拆分 确保流量均匀分布
                List<ExperimentGroup> experiments = experiment.getExperiments();
                int i = RandomUtils.groupByRandom(experiments.size());
                ExperimentGroup experimentGroup = experiments.get(i - 1);
                //实验标识
                String tag = experimentGroup.getTag();
                PerfUtils.perf("sun.napp.idConfidence.tag", Tag.of("type", tag));
                runTimeContext.getAbTestTags().put(tag, experimentGroup.getParam_val());
            }
        } catch (Exception e) {
            log.error("idConfidenceConstructError!",e);
            LogUtils.logError(runTimeContext.getRequestId(), "idConfidenceConstruct error!", e);
        }
    }

    public static void main(String[] args) {
        HashMap<String, String> stringStringHashMap = new HashMap<>();
        stringStringHashMap.put("id-unexperiment","false");
        stringStringHashMap.put("id-experiment","true");

        SunRR defaultInstance = SunRR.newBuilder().putAllAbTestTags(stringStringHashMap).setRequestId("11f58e1198a26092704433153c8565123035").build();
        System.out.println(defaultInstance);

    }
}