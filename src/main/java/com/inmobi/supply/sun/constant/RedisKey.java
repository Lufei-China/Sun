package com.inmobi.supply.sun.constant;

/**
 * redis key prefix
 * key = prefix_id
 *
 * @author remi.li
 */
public enum RedisKey {
    PLACEMENT_INFO_SET("pInfo_set"),
    PLACEMENT_BLOCK("block_id_set"),
    USER_AGENT_SET("agent_set"),
    DEVICE_SET("device_set"),
    APP_SET("app_set"),
    APP_DICT_SET("app_dict_set"),
    APP_DICT_BUNDLE_OS_SET("app_dict_bundle_os_set"),
    CAA_SET_1("caa_set_1"),
    CAA_SET_2("caa_set_2"),
    IPIP_SET_1("ipip_set_1"),
    IPIP_SET_2("ipip_set_2"),
    IPIP_SET_3("ipip_set_3"),

    PLACEMENT_INFO("pi"),
    APP_INFO("ai"),
    APP_DICT_INFO("ai_dict"),
    APP_DICT_BUNDLE_OS_INFO("ai_dict_bundle_os"),
    TEMPLATE_INFO("ti"),
    WIDGET_INFO("wi"),
    LANGUAGE_INFO("li"),
    USER_AGENT_INFO("uai"),
    DEVICE_INFO("di"),
    CAA_INFO("caa"),
    IPIP_INFO("ipip"),
    GETUI_DEMAND_PROJECT_TASK_INFO("getui_demand_task"),
    GETUI_RTB_PROJECT_TASK_INFO("getui_exchange_task"),
    DEVICE_IOS("device_ios"),

    BID_SUM_REQUEST_SUM_PREFIX("bsrsp"),
    BID_SUM_MARKET_REQUEST_SUM_PREFIX("bsmrsp"),
    DEVICEID_REQUEST_PREFIX("drp"),
    REVENUE_SUM_PREFIX("rsp"),
    REVENUE_MARKET_SUM_PREFIX("rmsp"),
    SUPPLY_SELECTION_GRADE_TAG("ssgt"),
    SUPPLY_SELECTION_RECOVERY_DATE("ssrd"),
    GETUI_REDIS_KEY_PREFIX("getui"),
    LOCAL_USER_GROUP_REDIS_KEY_PREFIX("user_group"),
    PLACEMENT_METRICS("pm"),
    DYNAMIC_FLOOR_RAISED_ECPM_PER_HOUR("dfreph"),
    DYNAMIC_FLOOR_DECLINED_ECPM_PER_HOUR("dfdeph"),
    DYNAMIC_FLOOR_ECPM_NUM("dfren"), //变更ecpm次数

    //动态底价
    RAISED_EXP_COUNT("dfrec"),
    RAISED_CHARGE("dfrc"),
    RAISED_PAY("dfrp"),
    RAISED_CLICK_COUNT("dfrcc"),

    DECLINED_EXP_COUNT("dfdec"),
    DECLINED_CHARGE("dfdc"),
    DECLINED_PAY("dfdp"),
    DECLINED_CLICK_COUNT("dfdcc"),

    //CAID相关
    //key: 12参数
    //value: CAID_Version
    TWELVE_PARAM_CAID("rpc"),
    //key: caid(key)
    //value: 12参数_Version
    CAID_TWELVE_PARAM("ctp"),
    //caid集合
    //value: caid
    CAID_SET("caid_set"),
    //netRev为0的
    NOVICE_NETREV_LIST("supply_novice_netrev0_list"),
    //新创建的广告位
    NOVICE_NEWER_LIST("supply_novice_new_list"),
    //请求比不稳定且bp<1的
    NOVICE_NOVICE_BP_REQ("supply_novice_bp_req"),
    // city 时间 广告位三维度的bp值小于1的
    NOVICE_NOVICE_BP("supply_novice_bp"),

    NOVICE_NOVICE_AL("supply_drop:"),

    NOVICE_NOVICE_HOUR("supply_novice_hour_bp"),

    CN_CONFIG_INFO("cn_config_info"),

    ALL_PLACEMENT_RCPM("all_placement_rcpm"),

    APP_INFO_BASE("app_info_base1"),

    APP_INFO_BASE_SET("app_info_base_set1"),

    MATERIAL_SANDBOX("materialSandbox"),

    MATERIAL_SANDBOX_SET("materialSandboxSet"),

    NOVICE_PLACEMENT_REQUEST_DAY("placement_request_day"),

    MIX_FLOW_TODAY_BURN_KEY("mix_flow_today_burn_key"),

    MIX_FLOW_TODAY_EVENT_KEY("mix_flow_today_event_key"),

    PUBLISHER_APPLIST_INFO("publisher_appList_info"),


    ;

    ;
    public String getPrefix() {
        return prefix;
    }

    private final String prefix;

    RedisKey(String prefix) {
        this.prefix = prefix;
    }

    public String of(Object suffix) {
        return prefix + "_" + suffix;
    }

    public String at(Object suffix) {
        return prefix + "@" + suffix;
    }

    public String of(String ...suffixs) {
        if (suffixs == null || suffixs.length == 0) {
            return "";
        }
        long length = suffixs.length;
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        for (int i = 0; i <= length - 1; i++) {
            sb.append("_");
            sb.append(suffixs[i]);
        }
        return sb.toString();
    }
}