package com.inmobi.supply.sun.constant;

import cn.inmobi.pb.api.CreativeType;
import cn.inmobi.pb.api.TrackingEvent;
import lombok.Getter;

import java.util.Arrays;

import static com.inmobi.supply.sun.constant.SymbolString.AND;
import static com.inmobi.supply.sun.constant.SymbolString.EQUAL;

/**
 * tracker宏定义
 * @author remi.li
 */
@Getter
public enum MacroConstant {
    //click tracker 宏
    DOWN_X("dX", "__DOWN_X__", TrackingEvent.CLICK),
    DOWN_Y("dY", "__DOWN_Y__", TrackingEvent.CLICK),
    UP_X("uX", "__UP_X__", TrackingEvent.CLICK),
    UP_Y("uY", "__UP_Y__", TrackingEvent.CLICK),
    HT("ht", "__HEIGHT__", TrackingEvent.CLICK),
    WT("wt", "__WIDTH__", TrackingEvent.CLICK),
    R_HT("r_ht", "__REQ_HEIGHT__", TrackingEvent.CLICK),
    R_WT("r_wt", "__REQ_WIDTH__", TrackingEvent.CLICK),
    C_AREA("c_area", "__CLICKAREA__", TrackingEvent.CLICK),

    //impression tracker 宏
    AUCTION_PRICE("auction_price", "__AUCTION_PRICE__", TrackingEvent.VIEW),
    AUCTION_PRICE_WN("auction_price", "__AUCTION_PRICE__", TrackingEvent.WIN_NOTICE),

    //video tracker 宏
    VD("vd", "__DURATION__", CreativeType.VIDEO),
    BT("bt", "__BEGIN_TIME__", CreativeType.VIDEO),
    ET("et", "__END_TIME__", CreativeType.VIDEO),
    BF("bf", "__PLAY_FIRST_FRAME__", CreativeType.VIDEO),
    EF("ef", "__PLAY_LAST_FRAME__", CreativeType.VIDEO),
    PS("ps", "__SCENE__", CreativeType.VIDEO),
    PT("pt", "__TYPE__", CreativeType.VIDEO),
    PB("pb", "__BEHAIVOR__", CreativeType.VIDEO),
    PST("pst", "__STATUS__", CreativeType.VIDEO),

    ;

    private String macroKey;
    private String macroValue;
    private TrackingEvent trackingEvent;
    private CreativeType creativeType;

    MacroConstant(String macroKey, String macroValue, TrackingEvent trackingEvent) {
        this.macroKey = macroKey;
        this.macroValue = macroValue;
        this.trackingEvent = trackingEvent;
        this.creativeType = CreativeType.UNKNOW_CREATIVE_TYPE;
    }

    MacroConstant(String macroKey, String macroValue, CreativeType creativeType) {
        this.macroKey = macroKey;
        this.macroValue = macroValue;
        this.trackingEvent = TrackingEvent.UNKNOWN_TRACKER_EVENT;
        this.creativeType = creativeType;
    }

    public static String getMacroStringByTrackingEvent(TrackingEvent trackingEvent) {
        StringBuilder sb = new StringBuilder();
        Arrays.stream(MacroConstant.values())
                .filter(macro -> macro.getTrackingEvent().equals(trackingEvent))
                .forEach(macro -> sb.append(AND).append(macro.getMacroKey()).append(EQUAL).append(macro.getMacroValue()));
        return sb.toString();
    }

    public static String getMacroStringByCreativeType(CreativeType creativeType) {
        StringBuilder sb = new StringBuilder();
        Arrays.stream(MacroConstant.values())
                .filter(macro -> macro.getCreativeType().equals(creativeType))
                .forEach(macro -> sb.append(AND).append(macro.getMacroKey()).append(EQUAL).append(macro.getMacroValue()));
        return sb.toString();
    }
}
