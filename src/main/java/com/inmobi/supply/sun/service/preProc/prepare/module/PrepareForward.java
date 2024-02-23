package com.inmobi.supply.sun.service.preProc.prepare.module;

import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.api.SourceType;
import cn.inmobi.pb.earth.entity.EarthResponse;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.adPool.IAdPool;
import com.inmobi.supply.sun.adPool.PerfAdPool;
import com.inmobi.supply.sun.adPool.SaturnAdPool;
import com.inmobi.supply.sun.config.dynamic.DynamicListConfig;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.runtime.CaidModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.LogUtils;
import com.inmobi.supply.sun.utils.PerfUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.inmobi.supply.sun.config.dynamic.DynamicListConfig.BES_PLACEMENT_LIST;
import static com.inmobi.supply.sun.constant.RuntimeConstant.IDFA_ZERO_MD5;
import static com.inmobi.supply.sun.constant.RuntimeConstant.IOS_ZERO_IDFA;

/**
 * build bidRequest
 *
 * @author remi.li
 */

@Service
@Slf4j
public class PrepareForward implements ModuleWorker<Integer> {
    private static int GOOD_SCORE = 90;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        try {
            LogUtils.logInfo(runTimeContext.getRequestId(),"PrepareForward");
            List<IAdPool> adPoolList = runTimeContext.getAdPoolList();
            if (CollectionUtils.isEmpty(adPoolList))
                return Mono.just(1);

            if(BES_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())){
                PerfUtils.perf("baidu.box.info","type","prepareForward");
                return Mono.just(1);
            }

            EarthResponse earthResponse = runTimeContext.getEarthResponse();
            if (null != earthResponse) {
                int score = earthResponse.getScore();
                PerfUtils.perf("prepareForward.remove.saturn","status","all");
                if (score < GOOD_SCORE && CollectionUtils.isNotEmpty(adPoolList)) {
                    Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                    LogUtils.logInfo(runTimeContext.getRequestId(), "prepareForward badScoreBefore {}", runTimeContext.getAdPoolList());
                    if(first.isPresent()){
                        runTimeContext.getAdPoolList().remove(first.get());
                        LogUtils.logInfo(runTimeContext.getRequestId(), "prepareForward badScoreAfter {}", runTimeContext.getAdPoolList());
                        PerfUtils.perf("prepareForward.remove.saturn","status","remove");
                    }
                }
            }

            // iOS 增加判断boot mark 和 update mark是否为空，如果两个mark不为空则暂时不过滤，后续filter中再处理
            Device device = runTimeContext.getDevice();
            boolean iosCaidFlag = false;
            boolean iosFlag = false;
            boolean androidFlag = false;
            if(!runTimeContext.getRequestType().isSdk() && OsType.IOS.equals(runTimeContext.getDevice().getOsType())){
                CaidModel caidModel = device.getCaidModel();
                iosCaidFlag = ObjectUtils.allNotNull(device, caidModel, device.getCaidModel().getMemorySize(), device.getCaidModel().getDiskSize())
                        && StringUtils.isNoneBlank(caidModel.getBootTimeSecond(), caidModel.getOsUpdateTimeSecond(), caidModel.getCountry(), caidModel.getLanguage(), caidModel.getPhoneNameMd5(), caidModel.getDeviceMachine(), caidModel.getModelCode(), caidModel.getTimeZone());
                iosFlag = !StringUtils.isAllBlank(device.getId().getIdfa(), device.getId().getIdfaMd5(), device.getBootMark(), device.getUpdateMark());
            }

            if(OsType.ANDROID.equals(runTimeContext.getDevice().getOsType())){
                androidFlag = !StringUtils.isAllBlank(device.getId().getOaid(), device.getId().getOaidMd5(), device.getId().getImeiMd5());
            }

            boolean isValidDevice = iosCaidFlag || iosFlag || androidFlag;
            if (!runTimeContext.getRequestType().isSdk() && !isValidDevice) {
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
                PerfUtils.perf("prepareForward.remove.saturn","status","isValidDevice");
                LogUtils.logInfo(runTimeContext.getRequestId(),"isValidDevice device:{}",JSON.toJSONString(device));
            }

            boolean iosIdfaZeroDevice = OsType.IOS.equals(runTimeContext.getDevice().getOsType())
                    && (IOS_ZERO_IDFA.equals(device.getId().getIdfa()) || IDFA_ZERO_MD5.equals(device.getId().getIdfa()))
                    && !iosCaidFlag && StringUtils.isAllBlank(device.getBootMark(), device.getUpdateMark());

            // IDFA 为000 且 没有十项参数 和 bootMark和UpdateMark
            if (!runTimeContext.getRequestType().isSdk() && iosIdfaZeroDevice) {
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
                PerfUtils.perf("prepareForward.remove.saturn","status","iosIdfaZeroDevice");
                LogUtils.logInfo(runTimeContext.getRequestId(),"iosIdfaZeroDevice device:{}",JSON.toJSONString(device));
            }

            // 若无UA，扔
            if (StringUtils.isBlank(device.getUserAgent())) {
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
                PerfUtils.perf("prepareForward.remove.saturn","status","uaIsNull");
                LogUtils.logInfo(runTimeContext.getRequestId(),"prepareForwardUaRemove device:{}",JSON.toJSONString(device));
            }

            // 若无ip，扔
            if (!runTimeContext.getRequestType().isSdk() && StringUtils.isAllBlank(runTimeContext.getBaseRequest().getDevice().getIp(),runTimeContext.getBaseRequest().getDevice().getIpv6())) {
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
                PerfUtils.perf("prepareForward.remove.saturn","status","ipIsNull");
                LogUtils.logInfo(runTimeContext.getRequestId(),"prepareForwardIpRemove device:{}",JSON.toJSONString(device));
            }

            // 限流之前先执行bp，因为bp要根据adpool执行不同逻辑，所以延迟到这里执行
            if (runTimeContext.isBelowExchangeRcost()) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "Below Exchange Rcost");
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.SATURN.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
            }
            if (runTimeContext.isBelowDemandRcost()) {
                LogUtils.logInfo(runTimeContext.getRequestId(), "Below Demand Rcost");
                Optional<IAdPool> first = adPoolList.stream().filter(iAdPool -> SourceType.PERF.getNumber() == iAdPool.sourceType().getNumber()).findFirst();
                first.ifPresent(iAdPool -> runTimeContext.getAdPoolList().remove(iAdPool));
            }

            //对不同的渠道进行限流
            int size = adPoolList.size();
            List<IAdPool> iAdPoolsNew = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                IAdPool iAdPool = adPoolList.get(i);
                //demand
                if (iAdPool.sourceType().getNumber() == SourceType.PERF.getNumber()) {
                    Entry entry = null;
                    try {
                        PerfUtils.perf("demandForwardEndPoint.block","status","all");
                        entry = SphU.entry("demandForwardEndPoint");
                        iAdPoolsNew.add(iAdPool);
                    } catch (BlockException e1) {
                        PerfUtils.perf("demandForwardEndPoint.block","status","block");
                    } catch (Exception e2) {
                        log.error("demandForwardEndPoint try flow error!", e2);
                    } finally {
                        if (entry != null) {
                            entry.exit();
                        }
                    }
                }

                /**
                 * 移动端补量只走demand
                 */
                if (DynamicListConfig.BRUSH_FLOW_PLACEMENT_LIST.get().contains(runTimeContext.getPlacementId())
                        && DynamicListConfig.BRUSH_FLOW_BUNDLE_LIST.get().contains(runTimeContext.getRequestBundleId())) {
                    PerfUtils.perf("sun.brush");
                    continue;
                }

                //saturn
                if (iAdPool.sourceType().getNumber() == SourceType.SATURN.getNumber()) {
                    Entry entry = null;
                    try {
                        PerfUtils.perf("exchangeForwardEndPoint.block","status","all");
                        entry = SphU.entry("exchangeForwardEndPoint");
                        iAdPoolsNew.add(iAdPool);
                    } catch (BlockException e1) {
                        PerfUtils.perf("exchangeForwardEndPoint.block","status","block");
                    } catch (Exception e2) {
                        log.error("exchangeForwardEndPoint try flow error!", e2);
                    } finally {
                        if (entry != null) {
                            entry.exit();
                        }
                    }
                }
            }
            runTimeContext.setAdPoolList(iAdPoolsNew);
            if (CollectionUtils.isEmpty(runTimeContext.getAdPoolList())) {
                throw new SunException(TermCode.FLOW_LIMIT, runTimeContext);
            }
            return Mono.just(1);
        } catch (SunException e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " SunException error {}", e);
            return Mono.error(e);
        } catch (Throwable e) {
            LogUtils.logError(runTimeContext.getRequestId(), this.getClass().getSimpleName() + " Unknown error {}", e);
            return Mono.error(new SunException(TermCode.SERVER_ERROR, runTimeContext, e));
        }
    }

}