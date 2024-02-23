package com.inmobi.supply.sun.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author zeki.zhao
 */
@Slf4j
public class CompareUtil {

    /**
     * 比较版本号的大小,前者大则返回一个正数,后者大返回一个负数,相等则返回0
     *
     * @param version1
     * @param version2
     * @return
     */
    public static int compareVersion(String version1, String version2) {
        int diff = -1;
        try {
            if (StringUtils.isBlank(version1) || StringUtils.isBlank(version2)) {
                log.error("compareVersion error:illegal params.");
            }
            String[] versionArray1 = version1.split("\\.");//注意此处为正则匹配，不能用"."；
            String[] versionArray2 = version2.split("\\.");
            int idx = 0;
            int minLength = Math.min(versionArray1.length, versionArray2.length);//取最小长度值
            diff = 0;
            while (idx < minLength && (diff = versionArray1[idx].length() - versionArray2[idx].length()) == 0//先比较长度
                    && (diff = versionArray1[idx].compareTo(versionArray2[idx])) == 0) {//再比较字符
                ++idx;
            }
            //如果已经分出大小，则直接返回，如果未分出大小，则再比较位数，有子版本的为大；
            diff = (diff != 0) ? diff : versionArray1.length - versionArray2.length;
        } catch (Exception e) {
            log.error("compareVersion error! v1:{} v2{}", version1, version2);
        }
        return diff;
    }
}
