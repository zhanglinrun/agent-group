package com.linrun.domain.groupbuy.model;

public enum GroupBuyDiscountType {

    BASE(0),
    TAG(1);

    private final int code;

    GroupBuyDiscountType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GroupBuyDiscountType parse(Integer code) {
        if (code == null || code == 0) {
            return BASE;
        }
        if (code == 1) {
            return TAG;
        }
        throw new IllegalArgumentException("unsupported discount type: " + code);
    }
}















