package com.inmobi.supply.sun.config.dynamic;

import cn.inmobi.pb.api.CreativeType;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.List;

@Slf4j
@SuppressWarnings("unchecked")
public enum DynamicListConfig implements DynamicConfig {
    SUPPORTED_AD_TYPE("supported.ad.type", "application", Collections.singletonList(1)),
    SUPPORTED_CREATIVE_TYPE("supported.ad.creativeType", "application",
            Lists.newArrayList(CreativeType.IMAGE_VALUE, CreativeType.VIDEO_VALUE)),
    TRUMP_DSP_CHANNEL_ID_LIST("trump.dsp.channel.id.list", "application", Collections.EMPTY_LIST),
    FIX_BID("fix.bid", "application", Collections.EMPTY_LIST),
    DYNAMIC_BID("dynamic.bid", "application", Collections.EMPTY_LIST),
    DEBUG_PLACEMENT_ID("debug.placementId", "application", Collections.EMPTY_LIST),
    REPLACE_TITLE_PUB("replace.title.pub", "application", Collections.EMPTY_LIST),
    IOS_SDK_COMPATIBLE_PLACEMENTS("ios.sdk.compatible.placements", "application", Collections.EMPTY_LIST),
    DYNAMIC_FLOOR_EXP_PLACEMENTS("dynamic.floor.exp.placements", "application", Collections.EMPTY_LIST), //参与动态底价实验的placementId
    BID_SHARING_SECOND_BID_PUB_WHITE_LIST("bid.sharing,second.pub.dsp.white.list", "application", Collections.EMPTY_LIST), //bid sharing 二价pubAccountId白名单列表
    RETURN_204("return.204.emptyBody", "application", Collections.EMPTY_LIST),
    WITHOUT_VB_CHANNEL_WHITE_LIST("without.vb.channel.white.list", "application", Collections.EMPTY_LIST),
    DEPRECATED_PLACEMENT_ID("deprecated.placement.id", "application", Collections.EMPTY_LIST),
    SKAN_PUBLISHER_LIST("skan.publisher.list", "application", Collections.EMPTY_LIST),
    NAPP_PLACEMENT_LIST("napp.placement.list", "application", Collections.EMPTY_LIST),
    SKAN_APP_LIST("skan.app.list", "application", Collections.EMPTY_LIST),
    //    DUCATION_PLACEMENT_LIST("ducation.placement.list", "application", Collections.EMPTY_LIST),
//    RATE_BLACK_PLACEMENT_LIST("rate.black.placement.list", "application", Collections.EMPTY_LIST),
    NOVICE_WHITE("novice.white", "sun.novice", Collections.EMPTY_LIST),
    NOVICE_WHITE_ACCOUNT("novice.white.account", "sun.novice", Collections.EMPTY_LIST),
    ADAPTER_LIST("adapter.list", "application", Collections.EMPTY_LIST),
    ADAPTER_BAIDU_LIST("adapter.baidu.list", "application", Collections.EMPTY_LIST),
    APP_BUNDLE_WHITE("app.bundle.white", "application", Collections.EMPTY_LIST),
    NOT_ADX_BUNDLE_WHITE("not.adx.bundle.white", "application", Collections.EMPTY_LIST),
    PUBLISHER_SUPPORT_CAID("publisher.caid.support", "application", Collections.EMPTY_LIST),
    ALLOW_AUTO_REDIRECTION("sdk.auto.redirection","application", Collections.EMPTY_LIST),
    ORTB_PUBLISHER_LIST("ortb.publisher.list", "application", Collections.EMPTY_LIST),
    CREATIVE_REPEAT_PUB_LIST("creative.repeat.pub.list","application", Collections.EMPTY_LIST),
    MIX_FLOW_RATE_LOW_LIST("mix.flow.rate.low.list","application", Collections.EMPTY_LIST),
    BRUSH_FLOW_PLACEMENT_LIST("brush.flow.placement.list", "application", Collections.EMPTY_LIST),
    BRUSH_FLOW_BUNDLE_LIST("brush.flow.bundle.list", "application", Collections.EMPTY_LIST),
    WHITE_PLACEMENT_LIST("white.placement.list", "application", Collections.EMPTY_LIST),
    BES_PLACEMENT_LIST("bes.placement.list", "application", Collections.EMPTY_LIST),
    DEMAND_302_LIST("demand.302.list", "application", Collections.EMPTY_LIST),
    PUB_APP_INFO_VALID_LIST("pub.app.info.valid.list", "application", Collections.EMPTY_LIST),


    ;

    DynamicListConfig(@NonNull String path, @NonNull String namespace, List<?> defaultValue) {
        this.path = path;
        this.namespace = namespace;
        this.defaultValue = defaultValue;
    }

    private final String path;

    private final String namespace;

    private final List<?> defaultValue;

    @Override
    public Function<String, List<?>> parseValue() {
        return new Function<String, List<?>>() {
            @Nullable
            @Override
            public List<?> apply(@Nullable String s) {
                try {
                    return (List<?>) ObjectMapperUtils.fromJSON(s, List.class);
                } catch (Exception e) {
                    log.error("DynamicMapBooleanConfig get error", e);
                    return defaultValue;
                }
            }
        };
    }

    @Override
    public List<?> get() {
        return get(path, namespace, defaultValue);
    }
}
