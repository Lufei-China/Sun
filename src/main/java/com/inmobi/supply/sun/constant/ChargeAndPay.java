package com.inmobi.supply.sun.constant;

/**
 * ChargeAndPay enum
 *
 * @author Leo.Liao
 */
public enum ChargeAndPay {
    CHARGE(0),
    PAY(1),
    ORIGIN_PAY(2),
    ;

    private int value;

    ChargeAndPay(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}