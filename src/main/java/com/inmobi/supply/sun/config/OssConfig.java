package com.inmobi.supply.sun.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zeki.zhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OssConfig {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
}
