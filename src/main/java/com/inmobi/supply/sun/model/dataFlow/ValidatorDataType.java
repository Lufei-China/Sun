package com.inmobi.supply.sun.model.dataFlow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * validator上下文
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ValidatorDataType extends DataType {
    private String ip;
}
