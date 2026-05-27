package com.linrun.domain.trade.model.aggregate;

import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;

public class TradePayOrderAggregate {

    private TradeOrderEntity tradeOrder;
    private PayOrderEntity payOrder;

    public TradeOrderEntity getTradeOrder() {
        return tradeOrder;
    }

    public void setTradeOrder(TradeOrderEntity tradeOrder) {
        this.tradeOrder = tradeOrder;
    }

    public PayOrderEntity getPayOrder() {
        return payOrder;
    }

    public void setPayOrder(PayOrderEntity payOrder) {
        this.payOrder = payOrder;
    }
}
