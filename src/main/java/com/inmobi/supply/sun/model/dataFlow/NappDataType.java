package com.inmobi.supply.sun.model.dataFlow;

import lombok.Builder;
import lombok.Data;

/**
 * napp产出data
 */
@Data
public class NappDataType extends DataType {
    private boolean drop;
    private int score;
}
