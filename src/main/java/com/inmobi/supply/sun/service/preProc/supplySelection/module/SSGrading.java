package com.inmobi.supply.sun.service.preProc.supplySelection.module;

import java.util.function.Function;

import cn.inmobi.pb.api.OsType;
import cn.inmobi.pb.api.SourceType;
import com.inmobi.supply.sun.constant.SupplySelectionGrade;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.impl.supplySelection.SupplySelectionRedisImp;
import com.inmobi.supply.sun.repo.redis.service.supplySelection.PlacementIdSourceTypeRepo;
import com.inmobi.supply.sun.repo.redis.service.supplySelection.SupplySelectionSumRepo;
import com.inmobi.supply.sun.service.ModuleWorker;
import com.inmobi.supply.sun.utils.WorkerUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import static com.inmobi.supply.sun.constant.SupplySelectionGrade.*;
import static com.inmobi.supply.sun.utils.WorkerUtils.dateGap;
import static com.inmobi.supply.sun.utils.WorkerUtils.todayDate;

/**
 * supply selection traffic grading process
 *
 * @author remi.li
 */
@Slf4j
@Service
public class SSGrading implements ModuleWorker<Integer> {
    private static final int DURATION = 7;
    private static final float RCPM_SCORE_PROP = 0.7F;
    private static final float SEGMENT_SCORE_PROP = 0.3F;
    private static final long RECOVERY_DURATION = 7;
    //@Autowired
    private PlacementIdSourceTypeRepo placementIdSourceTypeRepo;
    //@Autowired
    private SupplySelectionSumRepo supplySelectionSumRepo;
    //@Autowired
    private SupplySelectionRedisImp supplySelectionRedisImp;

    @Override
    public Mono<Integer> processMono(RunTimeContext runTimeContext) {
        String deviceId = getDeviceId(runTimeContext);
        String today = todayDate();
        return supplySelectionRedisImp.getDeviceIdRequestSum(deviceId)
                .defaultIfEmpty("0")
                .flatMap(requestNumForDeviceId -> {
                    int requestNum = Integer.valueOf(requestNumForDeviceId);
                    if (requestNum == 0 || requestNum <= 5000) {
                        return Mono.just(true);
                    } else {
                        return Mono.just(false);
                    }
                }).flatMap(isColdStart -> {
                    if (isColdStart) {
                        //if coldStart,return directly
                        return Mono.just(0);
                    }
                    long placementId = runTimeContext.getPlacementId();
                    Mono<String> perfGradeMono = supplySelectionRedisImp.getGradingByPlacementSourceType(placementId, SourceType.PERF_VALUE);
                    Mono<String> saturnGradeMono = supplySelectionRedisImp.getGradingByPlacementSourceType(placementId, SourceType.SATURN_VALUE);
                    Mono<Tuple2<String, String>> gradeTuple2 = perfGradeMono.zipWith(saturnGradeMono);
                    return gradeTuple2.flatMap(tuple2 -> {
                        Mono<Tuple2<String, String>> gradeThisTimeTuple2 = getGrade(placementId);
                        return gradeThisTimeTuple2.flatMap(gradeThisTime -> Flux.just(SourceType.PERF_VALUE, SourceType.SATURN_VALUE)
                                .flatMap(sourceType -> {
                                    String grade;
                                    String tagThisTime;
                                    if (sourceType == SourceType.PERF_VALUE) {
                                        grade = tuple2.getT1();
                                        tagThisTime = gradeThisTime.getT1();
                                    } else {
                                        grade = tuple2.getT2();
                                        tagThisTime = gradeThisTime.getT2();
                                    }
                                    if (StringUtils.isBlank(grade)) {
                                        runTimeContext.setGrade(Tuples.of(sourceType, tagThisTime));
                                        return supplySelectionRedisImp.saveGrade(placementId, sourceType, tagThisTime)
                                                .flatMap(res -> Mono.just(0));

                                    }
                                    if (grade.equals(SupplySelectionGrade.LEARNING.getShortFor())
                                            && tagThisTime.equals(HIGH.getShortFor())) {
                                        runTimeContext.setGrade(Tuples.of(sourceType, RECOVERY.getShortFor()));
                                        return supplySelectionRedisImp
                                                .saveGrade(placementId, sourceType, RECOVERY.getShortFor())
                                                .mergeWith(supplySelectionRedisImp.saveRecoveryGradeBeginDate(placementId, sourceType, today))
                                                .flatMap(res -> Mono.just(0));
                                    } else if (grade.equals(SupplySelectionGrade.RECOVERY.getShortFor())) {
                                        //recovery logic
                                        Mono<String> recoveryBeginDate = supplySelectionRedisImp.getRecoveryGradeBeginDate(placementId, sourceType);
                                        return recoveryBeginDate.flatMap(new Function<String, Mono<Integer>>() {
                                            @Override
                                            public Mono<Integer> apply(String beginDate) {
                                                long gap = dateGap(beginDate, today);
                                                if (gap <= RECOVERY_DURATION) {
                                                    //return directly
                                                    return Mono.just(0);
                                                } else {
                                                    //remove recovery tag
                                                    return supplySelectionRedisImp.delGrade(placementId, sourceType).flatMap(new Function<Boolean, Mono<Integer>>() {
                                                        @Override
                                                        public Mono<Integer> apply(Boolean aBoolean) {
                                                            runTimeContext.setGrade(Tuples.of(sourceType, tagThisTime));
                                                            return supplySelectionRedisImp.saveGrade(placementId, sourceType, tagThisTime)
                                                                    .flatMap(res -> Mono.just(0));
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                    }
                                    if (tagThisTime.equals(LOW.getShortFor())) {
                                        //1% traffic for learning
                                    }
                                    runTimeContext.setGrade(Tuples.of(sourceType, tagThisTime));
                                    return supplySelectionRedisImp.saveGrade(placementId, sourceType, tagThisTime)
                                            .flatMap(res -> Mono.just(0));
                                }).last());
                    });
                });
    }

    private Mono<Tuple2<String, String>> getGrade(long placementId) {
        return Flux.fromIterable(WorkerUtils.nDaysBefore(DURATION))
                .flatMap(date -> {
                    long bidSumPerf = placementIdSourceTypeRepo.getBidSum(date, placementId, SourceType.PERF_VALUE);
                    long requestSumPerf = placementIdSourceTypeRepo.getRequestSum(date, placementId, SourceType.PERF_VALUE);
                    long revenueSumPerf = placementIdSourceTypeRepo.getRevenueSum(date, placementId, SourceType.PERF_VALUE);

                    long bidSumSaturn = placementIdSourceTypeRepo.getBidSum(date, placementId, SourceType.SATURN_VALUE);
                    long requestSumSaturn = placementIdSourceTypeRepo.getRequestSum(date, placementId, SourceType.SATURN_VALUE);
                    long revenueSumSaturn = placementIdSourceTypeRepo.getRevenueSum(date, placementId, SourceType.SATURN_VALUE);

                    long marketBidSum = supplySelectionSumRepo.getMarketBidSum(date);
                    long marketRequestSum = supplySelectionSumRepo.getMarketRequestSum(date);
                    long marketRevenueSum = supplySelectionSumRepo.getMarketRevenueSum(date);
                    return Mono.just(new Sum(bidSumPerf, requestSumPerf, revenueSumPerf, bidSumSaturn, requestSumSaturn, revenueSumSaturn, marketBidSum, marketRequestSum, marketRevenueSum));
                }).reduce(new Sum(0, 0, 0, 0, 0, 0,0, 0, 0), (o1, o2) -> {
            long bidSumPerf = o1.getBidSumPerf() + o2.getBidSumPerf();
            long requestSumPerf = o1.getRequestSumPerf() + o2.getRequestSumPerf();
            long revenueSumPerf = o1.getRevenueSumPerf() + o2.getRequestSumPerf();

            long bidSumSaturn = o1.getBidSumSaturn() + o2.getBidSumSaturn();
            long requestSumSaturn = o1.getRequestSumSaturn() + o2.getRequestSumSaturn();
            long revenueSumSaturn = o1.getRevenueSumSaturn() + o2.getRequestSumSaturn();

            long marketBidSum = o1.getMarketBidSum() + o2.getMarketBidSum();
            long marketRequestSum = o1.getMarketRequestSum() + o2.getMarketRequestSum();
            long marketRevenueSum = o1.getMarketRevenueSum() + o2.getMarketRequestSum();
            return new Sum(bidSumPerf, requestSumPerf, revenueSumPerf, bidSumSaturn, requestSumSaturn, revenueSumSaturn, marketBidSum, marketRequestSum, marketRevenueSum);
        }).flatMap(sum -> {
            float rcpmPerf = (float) sum.getRevenueSumPerf() / sum.getRequestSumPerf();
            float segmentScorePerf = (float) sum.getBidSumPerf() / sum.getRequestSumPerf();
            float rcpmSaturn = (float) sum.getRevenueSumSaturn() / sum.getRequestSumSaturn();
            float segmentScoreSaturn = (float) sum.getBidSumSaturn() / sum.getRequestSumSaturn();

            float marketRcpmPerf = (float) sum.getMarketRevenueSum() / sum.getMarketRequestSum();
            float marketSegmentPerf = (float) sum.getMarketBidSum() / sum.getMarketRequestSum();

            float scorePerf = RCPM_SCORE_PROP * rcpmPerf + SEGMENT_SCORE_PROP * segmentScorePerf;
            float scoreSaturn = RCPM_SCORE_PROP * rcpmSaturn + SEGMENT_SCORE_PROP * segmentScoreSaturn;
            float marketScore = RCPM_SCORE_PROP * marketRcpmPerf + SEGMENT_SCORE_PROP * marketSegmentPerf;
            String perfTag = calculateGrade(scorePerf, marketScore);
            String saturnTag = calculateGrade(scoreSaturn, marketScore);
            return Mono.just(Tuples.of(perfTag, saturnTag));
        });
    }

    private String calculateGrade(float score, float marketScore) {
        if (score - marketScore >= 0.2 * marketScore) {
            return HIGH.getShortFor();
        } else if (marketScore - score >= 0.2 * marketScore) {
            return SupplySelectionGrade.LOW.getShortFor();
        } else {
            return SupplySelectionGrade.MEDIUM.getShortFor();
        }
    }

    private String getDeviceId(RunTimeContext runTimeContext) {
        String deviceId = "";
        OsType osType = runTimeContext.getDevice().getOsType();
        if (osType == OsType.IOS) {
            deviceId = runTimeContext.getDevice().getId().getIdfa();
            if (StringUtils.isBlank(deviceId)) {
                deviceId = runTimeContext.getDevice().getId().getIdfv();
            }
        }
        if (osType == OsType.ANDROID) {
            deviceId = runTimeContext.getDevice().getId().getImeiMd5();
            if (StringUtils.isBlank(deviceId)) {
                deviceId = runTimeContext.getDevice().getId().getOaid();
            }
            if (StringUtils.isBlank(deviceId)) {
                deviceId = runTimeContext.getDevice().getId().getO1();
            }
            if (StringUtils.isBlank(deviceId)) {
                deviceId = runTimeContext.getDevice().getId().getUm5();
            }
            if (StringUtils.isBlank(deviceId)) {
                deviceId = runTimeContext.getDevice().getId().getImeiSha1();
            }
        }
        return deviceId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Sum {
        private long bidSumPerf;
        private long requestSumPerf;
        private long revenueSumPerf;

        private long bidSumSaturn;
        private long requestSumSaturn;
        private long revenueSumSaturn;

        private long marketBidSum;
        private long marketRequestSum;
        private long marketRevenueSum;
    }
}
