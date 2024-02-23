package com.inmobi.supply.sun.model.material;

import lombok.Builder;
import lombok.Data;

/**
 * 送审通过素材实体
 */
@Data
@Builder
public class MaterialInfo{

    /**
     * 素材宽_素材高 组成的分类 示例： 720_1280
     */
    private String creativeKey;

    /**
     * 送审素材id
     */
    private String creativeId;

    /**
     * 素材类型
     */
    private int adType;

    /**
     * 开发者id
     */
    private String publisherId;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片url
     */
    private String imgUrl;

    /**
     * 图片宽
     */
    private int width;

    /**
     * 图片高
     */
    private int height;

    /**
     * 交互类型
     */
    private int interactionType;
}