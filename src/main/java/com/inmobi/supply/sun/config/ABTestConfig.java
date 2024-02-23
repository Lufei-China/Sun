package com.inmobi.supply.sun.config;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ABTestConfig {

    /**
     * APP 名称
     */
    private String app;

    /**
     * app对应规则
     */
    private Map<String,Experiment> layers;

    public static void main(String[] args) {
        String json = "{\n" +
                "    \"app\":\"earth\",\n" +
                "    \"layers\":{\n" +
                "        \"anti-fraud\":{\n" +
                "            \"id\":3,\n" +
                "            \"name\":\"实时反作弊\",\n" +
                "            \"buckets\":[\n" +
                "                1,\n" +
                "                50,\n" +
                "                51,\n" +
                "                100\n" +
                "            ],\n" +
                "            \"experiments\":[\n" +
                "                {\n" +
                "                    \"name\":\"实验组\",\n" +
                "                    \"tag\":\"id-experiment\",\n" +
                "                    \"param\":\"id-inconfidence\",\n" +
                "                    \"param_val\":\"true\",\n" +
                "                    \"bucket\":[\n" +
                "                        1,\n" +
                "                        50\n" +
                "                    ],\n" +
                "                    \"whitelist\":[\n" +
                "\n" +
                "                    ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"name\":\"对照组\",\n" +
                "                    \"tag\":\"id-unexperiment\",\n" +
                "                    \"param\":\"id-inconfidence\",\n" +
                "                    \"param_val\":\"false\",\n" +
                "                    \"bucket\":[\n" +
                "                        51,\n" +
                "                        100\n" +
                "                    ],\n" +
                "                    \"whitelist\":[\n" +
                "\n" +
                "                    ]\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ABTestConfig abTestConfig = JSON.parseObject(json, ABTestConfig.class);
        System.out.println(JSON.toJSONString(abTestConfig));
    }

}

