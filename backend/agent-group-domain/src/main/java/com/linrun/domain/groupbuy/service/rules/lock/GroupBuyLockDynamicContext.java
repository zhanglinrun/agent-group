package com.linrun.domain.groupbuy.service.rules.lock;

public class GroupBuyLockDynamicContext {

    private Integer userTakeOrderCount;

    public Integer getUserTakeOrderCount() {
        return userTakeOrderCount;
    }

    public void setUserTakeOrderCount(Integer userTakeOrderCount) {
        this.userTakeOrderCount = userTakeOrderCount;
    }
}
