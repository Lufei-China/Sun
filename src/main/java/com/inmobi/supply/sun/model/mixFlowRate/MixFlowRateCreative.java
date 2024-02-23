package com.inmobi.supply.sun.model.mixFlowRate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inmobi.supply.sun.model.sandbox.MockAd;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class MixFlowRateCreative {

    MockAd.Image image;
    @JsonProperty("target_url")
    String targetUrl;
    @JsonProperty("title")
    String title;
    @JsonProperty("description")
    String description;

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
}