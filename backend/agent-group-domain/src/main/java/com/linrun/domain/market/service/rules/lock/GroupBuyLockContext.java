package com.linrun.domain.market.service.rules.lock;

import com.linrun.api.dto.LockGroupBuyOrderRequest;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyTeam;

import java.time.LocalDateTime;

public class GroupBuyLockContext {

    private final LockGroupBuyOrderRequest request;
    private final QuotaProduct product;
    private final GroupBuyActivity activity;
    private final LocalDateTime now;
    private GroupBuyTeam team;
    private boolean teamStockOccupied;

    public GroupBuyLockContext(LockGroupBuyOrderRequest request,
                               QuotaProduct product,
                               GroupBuyActivity activity,
                               LocalDateTime now) {
        this.request = request;
        this.product = product;
        this.activity = activity;
        this.now = now;
    }

    public LockGroupBuyOrderRequest getRequest() {
        return request;
    }

    public QuotaProduct getProduct() {
        return product;
    }

    public GroupBuyActivity getActivity() {
        return activity;
    }

    public LocalDateTime getNow() {
        return now;
    }

    public GroupBuyTeam getTeam() {
        return team;
    }

    public void setTeam(GroupBuyTeam team) {
        this.team = team;
    }

    public boolean isTeamStockOccupied() {
        return teamStockOccupied;
    }

    public void setTeamStockOccupied(boolean teamStockOccupied) {
        this.teamStockOccupied = teamStockOccupied;
    }
}















