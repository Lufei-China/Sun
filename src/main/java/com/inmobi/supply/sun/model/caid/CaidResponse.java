package com.inmobi.supply.sun.model.caid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.annotation.Nonnull;

/**
 * @author remi.li
 * caid response
 */
@Data
public class CaidResponse {
    @JsonProperty("version")
    @Nonnull
    String version;

    @JsonProperty("caid")
    @Nonnull
    String caid;
}
