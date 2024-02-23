package com.inmobi.supply.sun.service.preProc.call;

import cn.inmobi.pb.api.*;
import com.google.common.util.concurrent.RateLimiter;
import com.inmobi.supply.component.model.redis.MaterialSandbox;
import com.inmobi.supply.component.model.redis.placement.TestDevice;
import com.inmobi.supply.sun.constant.RequestHeader;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.repo.redis.service.SandBoxRepo;
import com.inmobi.supply.sun.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 沙箱环境 mock广告
 *
 * @author remi.li
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class SandboxService {
    @Autowired
    private SandBoxRepo sandBoxRepo;

    private RateLimiter rateLimiter = RateLimiter.create(1);

    @Nullable
    public BidResponse mockAd(RunTimeContext runTimeContext) {
        /**
         * 单机每秒通过2个mock ad
         */
        if (!rateLimiter.tryAcquire()) {
            return null;
        }
        BidRequest bidRequest = runTimeContext.getBidRequest();
        StringBuilder mockAdKey = new StringBuilder();
        //1.选择interactionType
        List<Integer> interactionTypeList = new ArrayList<>();
        // 1.1 优先选header传过来的
        String customInteractionType = runTimeContext.getHeaders().get(RequestHeader.MOCK_AD_INTERACTION_HEADER);
        if (StringUtils.isNotEmpty(customInteractionType)) {
            interactionTypeList.add(Integer.valueOf(customInteractionType));
        }
        // 1.2选testDevice指定的
        if (interactionTypeList.size() == 0) {
            Integer interactionType = null;
            List<TestDevice> testDeviceList = runTimeContext.getTestDeviceList();

            if (CollectionUtils.isNotEmpty(testDeviceList)) {
                TestDevice testDevice = testDeviceList.get(0);

                int interactionTypeInclusion = testDevice.getInteractionType();
                switch (interactionTypeInclusion) {
                    //h5
                    case 1:
                        interactionType = InteractionType.OPEN_IN_APP_VALUE;
                        break;
                    //deeplink
                    case 2:
                        //Universal
                    case 3:
                        interactionType = InteractionType.DEEPLINK_VALUE;
                        break;
                    //Universal
                    case 4:
                        interactionType = InteractionType.DOWNLOAD_VALUE;
                        break;
                    //wechat
                    case 6:
                        interactionType = InteractionType.WECHAT_MINI_PROGRAM_VALUE;
                        break;
                    default:
                }
                if (ObjectUtils.isNotEmpty(interactionType)) {
                    interactionTypeList.add(interactionType);
                }
            }

        }
        // 1.3 选请求体中要求的
        if (interactionTypeList.size() == 0) {
            interactionTypeList = bidRequest.getAdslots(0).getAcceptedInteractionTypesList();
        }
        //2.选择creativeType
        List<Integer> creativeTypeList = new ArrayList<>();
        String customCreativeType = runTimeContext.getHeaders().get(RequestHeader.MOCK_AD_CREATIVE_TYPE_HEADER);
        if (StringUtils.isNotEmpty(customCreativeType)) {
            creativeTypeList.add(Integer.valueOf(customCreativeType));
        }
        if (creativeTypeList.size() == 0) {
            List<Integer> acceptedCreativeType = bidRequest.getAdslots(0).getAcceptedCreativeTypeRatiosList().stream().map(creativeTypeRatio -> creativeTypeRatio.getCreativeType()).collect(Collectors.toList());
            if (acceptedCreativeType.size() > 0) {
                creativeTypeList.addAll(acceptedCreativeType);
            }
        }
        // 3.选择Ratio
        List<CreativeTypeRatio> creativeTypeRatioList = bidRequest.getAdslots(0).getAcceptedCreativeTypeRatiosList();
        int creativeTypeRandom = (new Random()).nextInt(creativeTypeRatioList.size());
        CreativeTypeRatio creativeTypeRatio = creativeTypeRatioList.get(creativeTypeRandom);
        String[] widthHeightList = StringUtils.split(creativeTypeRatio.getWhRatio(), ":");
        String widthHeight = "";
        if (widthHeightList.length >= 2) {
            widthHeight = widthHeightList[0] + "_" + widthHeightList[1];
        }
        mockAdKey
                .append(runTimeContext.getPlacementModel().getType())
                .append("_")
                .append(runTimeContext.getDevice().getOsType().getNumber())
                /*.append("_")
                .append(interactionType)
                .append("_")
                .append(creativeType)*/
                .append("_")
                .append(widthHeight);

        LogUtils.logInfo(runTimeContext.getRequestId(), "mockAdKey={} interactionTypeList{} creativeTypeList{} placementId={}", mockAdKey, interactionTypeList, creativeTypeList, runTimeContext.getPlacementId());
        MaterialSandbox materialSandbox = sandBoxRepo.getMockAdMap(mockAdKey.toString(), interactionTypeList, creativeTypeList);
        LogUtils.logInfo(runTimeContext.getRequestId(), "mockAd={} placementId={}", materialSandbox, runTimeContext.getPlacementId());
        BidResponse.Builder responseBuilder = BidResponse.newBuilder();
        Ad.Builder adBuilder = Ad.newBuilder();
        responseBuilder.setRequestId(runTimeContext.getRequestId());
        responseBuilder.setProcessingTimeMs(80);
        responseBuilder.setSourceType(SourceType.SATURN_VALUE);
        responseBuilder.setSaturnDsp("mockRTB");
        responseBuilder.setCustomRevShare(SourceType.SATURN_VALUE);
        if (materialSandbox != null) {
            adBuilder.setAdId("mockAdId");
            adBuilder.setBidPrice(5144);
            adBuilder.setBidType(1);
            adBuilder.setAdvBidPrice(3928);
            adBuilder.setSecondBidPrice(3528);
            adBuilder.setAdvSettlePrice(4244);
            adBuilder.setSaturnChannelId("mockSaturnChannel");
            adBuilder.setSaturnSegmentId("mockSaturnSegmentID");
            adBuilder.setSettleType(1);
            adBuilder.setNetDeduction(0.3525);
            adBuilder.setAdpoolDeduction(0.14);
            MaterialMeta.Builder creativeBuilder = MaterialMeta.newBuilder();
            creativeBuilder.setCreativeType(materialSandbox.getCreativeType())
                    .setInteractionType(materialSandbox.getInteractionType())
                    .setTitle(Optional.ofNullable(materialSandbox.getTitle()).orElse(""))
                    .setDescription(Optional.ofNullable(materialSandbox.getDescribe()).orElse(""))
                    .setIcon(Optional.ofNullable(materialSandbox.getIconUrl()).orElse(""));
            if (materialSandbox.getCreativeType() == CreativeType.IMAGE_VALUE) {
                creativeBuilder.setImage(Image.newBuilder()
                        .setUrl(Optional.ofNullable(materialSandbox.getImageUrl()).orElse(""))
                        .setWidth(Optional.ofNullable(materialSandbox.getWidth()).orElse(0))
                        .setHeight(Optional.ofNullable(materialSandbox.getHeight()).orElse(0)));
            }
            if (materialSandbox.getCreativeType() == CreativeType.VIDEO_VALUE) {
                creativeBuilder.setVideo(Video.newBuilder()
                        .setVideoUrl(Optional.ofNullable(materialSandbox.getVideoUrl()).orElse(""))
                        .setCoverUrl(Optional.ofNullable(materialSandbox.getCoverUrl()).orElse(""))
                        .setCoverHeight(Optional.ofNullable(materialSandbox.getHeight()).orElse(0))
                        .setCoverWidth(Optional.ofNullable(materialSandbox.getWidth()).orElse(0))
                        .setVideoDuration(10));
            }
            switch (materialSandbox.getInteractionType()) {
                case InteractionType.OPEN_IN_APP_VALUE:
                    creativeBuilder.setTargetUrl(Optional.ofNullable(materialSandbox.getTargetUrl()).orElse(""));
                    ;
                    break;
                case InteractionType.DOWNLOAD_VALUE:
                    creativeBuilder.setTargetUrl(Optional.ofNullable(materialSandbox.getFallbackUrl()).orElse(""));
                    ;
                    creativeBuilder.setDownloadUrl(Optional.ofNullable(materialSandbox.getTargetUrl()).orElse(""));
                    ;
                    creativeBuilder.setPackageName("com.campmobile.snowcamera");
                    break;
                case InteractionType.DEEPLINK_VALUE:
                    creativeBuilder.setTargetUrl(Optional.ofNullable(materialSandbox.getFallbackUrl()).orElse(""));
                    ;
                    creativeBuilder.setDeeplinkUrl(Optional.ofNullable(materialSandbox.getTargetUrl()).orElse(""));
                    ;
                    creativeBuilder.setAppName("com.360buy.jdmobile");
                    break;
                case InteractionType.WECHAT_MINI_PROGRAM_VALUE:
                    creativeBuilder.setTargetUrl(Optional.ofNullable(materialSandbox.getFallbackUrl()).orElse(""));
                    creativeBuilder.setWechatMiniProgramOriginId(Optional.ofNullable(materialSandbox.getOriginId()).orElse(""));
                    creativeBuilder.setWechatMiniProgramPath(Optional.ofNullable(materialSandbox.getTargetUrl()).orElse(""));
                    break;
                default:
            }
            /*if (CollectionUtils.isNotEmpty(materialSandbox.getImpUrls())) {
                creativeBuilder.addAllImpUrl(mockAd.getImpUrls());
            }
            if (CollectionUtils.isNotEmpty(mockAd.getClickUrl())) {
                creativeBuilder.addAllClickUrl(mockAd.getClickUrl());
            }*/
            adBuilder.setCreative(creativeBuilder);
            responseBuilder.addAds(adBuilder);
            return responseBuilder.build();
        } else {
            return null;
        }
    }
}