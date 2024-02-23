package com.inmobi.supply.sun.config;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实验规则
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Experiment {

    /**
     * 实验参数
     */
    private List<ExperimentGroup> experiments;

    /**
     * 实验分桶
     */
    private List<Integer> buckets;

    /**
     * 实验名称
     */
    private String name;

    /**
     * 实验id
     */
    private Integer id;
}



