package com.linrun.trigger.service.groupbuy.refund.rule;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;

public class GroupBuyRefundContext {

    private final RefundGroupBuyOrderRequest request;
    private TradeOrderEntity tradeOrder;
    private PayOrderEntity payOrder;
    private GroupBuyCompensationResponse response;

    public GroupBuyRefundContext(RefundGroupBuyOrderRequest request) {
        this.request = request;
    }

    public RefundGroupBuyOrderRequest getRequest() {
        return request;
    }

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

    public GroupBuyCompensationResponse getResponse() {
        return response;
    }

    public void setResponse(GroupBuyCompensationResponse response) {
        this.response = response;
    }
}
