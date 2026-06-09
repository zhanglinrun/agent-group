package com.linrun.domain.groupbuy.service.rules.refund;

import com.linrun.api.dto.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;

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















