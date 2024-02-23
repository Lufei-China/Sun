package com.inmobi.supply.sun.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实验规则中的分组
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExperimentGroup {

    /**
     * 实验名称
     */
    private String name;

    /**
     * tag 标识
     */
    private String tag;

    /**
     * 实验设定的参数名称
     */
    private String param;

    /**
     * 实验设定的参数具体值
     */
    private String param_val;

    /**
     * 分桶
     */
    private List<Integer> bucket;

    /**
     * 白名单
     */
    private List<Integer> whitelist;
}