package com.inmobi.supply.sun.model.runtime;

import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Caid 12项参数
 *
 * @author remi.li
 */
@Data
public class CaidModel {
    private String bootTimeSecond; //开机时间，秒级时间戳 示例：1595214620.383940
    private String country = "CN"; //国家
    private String language; //语言
    private String phoneNameMd5; //设备名称Md5
    private String osv; //设备版本
    private String deviceMachine; //设备machine
    private String carrier; //运营商
    private Double memorySize; //物理内存
    private Double diskSize; //磁盘容量
    private String osUpdateTimeSecond; //系统更新时间
    private String modelCode; //apple model code
    private String timeZone; //时区

    private String caid;
    private String caidVersion;
    private String preCaid;
    private String preCaidVersion;

    private boolean twelveParamEmpty = true;

    public String twelveParamMd5Key() {
        if (twelveParamEmpty) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        sb.append(bootTimeSecond)
                .append(country)
                .append(language)
                .append(phoneNameMd5)
                .append(osv)
                .append(deviceMachine)
                .append(carrier)
                .append(memorySize)
                .append(diskSize)
                .append(osUpdateTimeSecond)
                .append(modelCode)
                .append(timeZone);
        return DigestUtils.md5Hex(sb.toString());
    }
}