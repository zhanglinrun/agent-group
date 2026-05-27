package com.linrun.domain.trade.service.groupbuy.settlement;

import com.linrun.domain.activity.model.GroupBuySettlementResult;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;

import java.util.List;

public class GroupBuySettlementContext {

    private final TradeOrderEntity tradeOrder;
    private GroupBuySettlementResult settlementResult;
    private List<String> settledOrderIds = List.of();
    private boolean stopped;

    public GroupBuySettlementContext(TradeOrderEntity tradeOrder) {
        this.tradeOrder = tradeOrder;
    }

    public TradeOrderEntity getTradeOrder() {
        return tradeOrder;
    }

    public GroupBuySettlementResult getSettlementResult() {
        return settlementResult;
    }

    public void setSettlementResult(GroupBuySettlementResult settlementResult) {
        this.settlementResult = settlementResult;
    }

    public List<String> getSettledOrderIds() {
        return settledOrderIds;
    }

    public void setSettledOrderIds(List<String> settledOrderIds) {
        this.settledOrderIds = settledOrderIds == null ? List.of() : settledOrderIds;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stop() {
        this.stopped = true;
    }
}
