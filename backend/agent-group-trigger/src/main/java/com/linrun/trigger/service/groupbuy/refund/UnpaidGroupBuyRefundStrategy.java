package com.linrun.trigger.service.groupbuy.refund;

import com.linrun.api.marketing.request.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.trigger.service.GroupBuyCompensationService;

public class UnpaidGroupBuyRefundStrategy implements GroupBuyRefundStrategy {

    private final GroupBuyCompensationService groupBuyCompensationService;

    public UnpaidGroupBuyRefundStrategy(GroupBuyCompensationService groupBuyCompensationService) {
        this.groupBuyCompensationService = groupBuyCompensationService;
    }

    @Override
    public boolean supports(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        return PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())
                || TradeOrderStatusEnumVO.CREATE.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatusEnumVO.PAY_WAIT.equals(tradeOrder.getOrderStatus());
    }

    @Override
    public GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request) {
        CloseUnpaidGroupBuyOrderRequest closeRequest = new CloseUnpaidGroupBuyOrderRequest();
        closeRequest.setOrderId(request.getOrderId());
        closeRequest.setCloseTime(request.getRefundTime());
        return groupBuyCompensationService.closeUnpaid(closeRequest);
    }
}
