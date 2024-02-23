package com.inmobi.supply.sun.model.runtime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

/**
 * 代码流程追踪
 *
 * @author gavin.xu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trace {

    private LinkedHashMap<String, Object> traceResponse;

    @JsonAlias("Normalizer")
    private NormalizerTrace normalizerTrace;

    @JsonAlias("Validator")
    private ValidatorTrace validatorTrace;

    @JsonAlias("Handset")
    private HandsetTrace handsetTrace;

    private Trace(){

    }

    public static Trace getInstance(){
        Trace trace = new Trace();
        trace.normalizerTrace = new NormalizerTrace();
        trace.validatorTrace = new ValidatorTrace();
        trace.traceResponse = new LinkedHashMap<>();
        return trace;
    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class NormalizerTrace{
        public boolean normalizerRequestIdInvoked;
        public boolean normalizerHeadersInvoked;
        public boolean normalizerHostAndUrlInvoked;
        public boolean normalizerRequestInvoked;
        public boolean normalizerIpInvoked;
        public boolean normalizerLanguageInvoked;
        public boolean normalizerLatLongInvoked;
        public boolean normalizerCookieInvoked;
        public boolean normalizerIos14Invoked;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ValidatorTrace{
        public boolean validatorRequestFieldInvoked;
        public boolean validatorPlacementInvoked;
        public boolean validatorHandSetInvoked;
        public boolean validatorIntegrationInvoked;
        public boolean validatorIdMappingInvoked;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class HandsetTrace{
        public String externalId;
        public String modelName;
    }
}