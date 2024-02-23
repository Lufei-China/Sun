package com.inmobi.supply.sun.model.appList;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppListInfo {

    /**
     * tagId
     */
    private String tagId;

    /**
     * 包名
     */
    private String bundleId;
}