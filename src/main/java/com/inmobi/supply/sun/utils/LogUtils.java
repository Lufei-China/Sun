package com.inmobi.supply.sun.utils;


import com.inmobi.supply.framework.common.utils.SpringUtils;
import com.inmobi.supply.sun.config.prometheus.PrometheusOverlord;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 功能概述
 * className:      LogUtils
 * package:        com.inmobi.supply.sun.utils
 * author:         Gavin.Xu
 * date:           2022/1/14
 */
@Slf4j
public class LogUtils {
    private static final String env = System.getenv("cluster");
    private static final String uat = "uat";
    /**
     *
     * @param requestId 全局唯一id
     * @param placeHolder 日志前缀+占位符
     * @param vars 占位变量
     */
    public static void logDebug(String requestId,String placeHolder,Object... vars){
        if (uat.equals(env)){
            log.debug(requestId + "#" + placeHolder, vars);
        }
    }

    public static void logInfo(String requestId,String placeHolder,Object... vars){
        if (uat.equals(env)) {
            log.info(requestId + "#" + placeHolder, vars);
        }
    }

    public static void logWarn(String requestId,String placeHolder,Object... vars){
        if (uat.equals(env)) {
            log.warn(requestId + "#" + placeHolder, vars);
        }
    }

    public static void logError(String requestId,String placeHolder,Throwable e){
        if (uat.equals(env)) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            e.printStackTrace(writer);
            StringBuffer buffer = stringWriter.getBuffer();
            log.error(requestId + "#" + placeHolder, buffer);
        }
    }

    public static void logTask(String placeHolder,Object... vars){
        if (uat.equals(env)) {
            log.info("[TASK] " + placeHolder, vars);
        }
    }

    public static boolean isUat(){
        return uat.equals(env);
    }

    public static void main(String[] args) {
        String home = System.getenv("HOME");
        System.out.println(home);
    }
}
