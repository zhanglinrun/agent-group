package com.linrun.domain.activity.model;

public class GroupBuyLockResult {

    private GroupBuyOrderLock orderLock;
    private GroupBuyTeam team;
    private boolean repeated;

    public GroupBuyLockResult() {
    }

    public GroupBuyLockResult(GroupBuyOrderLock orderLock, GroupBuyTeam team, boolean repeated) {
        this.orderLock = orderLock;
        this.team = team;
        this.repeated = repeated;
    }

    public GroupBuyOrderLock getOrderLock() {
        return orderLock;
    }

    public void setOrderLock(GroupBuyOrderLock orderLock) {
        this.orderLock = orderLock;
    }

    public GroupBuyTeam getTeam() {
        return team;
    }

    public void setTeam(GroupBuyTeam team) {
        this.team = team;
    }

    public boolean isRepeated() {
        return repeated;
    }

    public void setRepeated(boolean repeated) {
        this.repeated = repeated;
    }
}
