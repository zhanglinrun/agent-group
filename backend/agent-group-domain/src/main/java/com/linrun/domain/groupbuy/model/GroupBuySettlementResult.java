package com.linrun.domain.groupbuy.model;

public class GroupBuySettlementResult {

    private GroupBuyOrderLock orderLock;
    private GroupBuyTeam team;
    private boolean repeated;

    public GroupBuySettlementResult() {
    }

    public GroupBuySettlementResult(GroupBuyOrderLock orderLock, GroupBuyTeam team, boolean repeated) {
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
