package com.inmobi.supply.sun.constant;

/**
 * SupplySelectionGrade
 *
 * @author remi.li
 */
public enum SupplySelectionGrade {
    HIGH("H"),
    MEDIUM("M"),
    LOW("L"),
    LEARNING("LEARN"),
    RECOVERY("R"),
    ;

    public String getShortFor() {
        return shortFor;
    }

    private String shortFor;

    SupplySelectionGrade(String shortFor) {
        this.shortFor = shortFor;
    }
}
