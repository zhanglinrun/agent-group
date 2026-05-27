package com.linrun.trigger.service.groupbuy.lock;

import com.linrun.api.marketing.request.LockGroupBuyOrderRequest;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyTeam;

import java.time.LocalDateTime;

public class GroupBuyLockContext {

    private final LockGroupBuyOrderRequest request;
    private final GuideProduct product;
    private final GroupBuyActivity activity;
    private final LocalDateTime now;
    private GroupBuyTeam team;
    private boolean teamStockOccupied;

    public GroupBuyLockContext(LockGroupBuyOrderRequest request,
                               GuideProduct product,
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

    public GuideProduct getProduct() {
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
