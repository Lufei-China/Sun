package com.inmobi.supply.sun.model.sandbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * sandbox模拟返回model
 * @author remi.li
 */
@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class MockAd {
    Image image;
    Video video;
    @JsonProperty("target_url")
    String targetUrl;
    @JsonProperty("download_url")
    String downloadUrl;
    @JsonProperty("deeplink_url")
    String deeplinkUrl;
    @JsonProperty("title")
    String title;
    @JsonProperty("description")
    String description;
    @JsonProperty("imp_url")
    List<String> impUrls;
    @JsonProperty("click_url")
    List<String> clickUrl;
    @JsonProperty("origin_id")
    String originId;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Image {
        String url;
        Integer width;
        Integer height;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Video {
        @JsonProperty("cover_height")
        Integer coverHeight;
        @JsonProperty("cover_width")
        Integer coverWidth;
        @JsonProperty("video_duration")
        Integer videoDuration;
        @JsonProperty("video_url")
        String videoUrl;
        @JsonProperty("cover_url")
        String coverUrl;
    }
}