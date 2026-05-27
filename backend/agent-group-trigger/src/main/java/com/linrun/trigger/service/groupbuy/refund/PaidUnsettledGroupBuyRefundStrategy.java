package com.linrun.trigger.service.groupbuy.refund;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.trigger.service.GroupBuyCompensationService;
import com.linrun.trigger.service.PaymentService;

public class PaidUnsettledGroupBuyRefundStrategy implements GroupBuyRefundStrategy {

    private final PaymentService paymentService;
    private final GroupBuyCompensationService groupBuyCompensationService;

    public PaidUnsettledGroupBuyRefundStrategy(PaymentService paymentService,
                                               GroupBuyCompensationService groupBuyCompensationService) {
        this.paymentService = paymentService;
        this.groupBuyCompensationService = groupBuyCompensationService;
    }

    @Override
    public boolean supports(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        return PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())
                && TradeOrderStatusEnumVO.PAY_SUCCESS.equals(tradeOrder.getOrderStatus());
    }

    @Override
    public GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request) {
        paymentService.refund(toPaymentRequest(request));
        return groupBuyCompensationService.releaseRefundedOrder(request);
    }

    protected RefundPaymentRequest toPaymentRequest(RefundGroupBuyOrderRequest request) {
        RefundPaymentRequest paymentRequest = new RefundPaymentRequest();
        paymentRequest.setOrderId(request.getOrderId());
        paymentRequest.setRefundReason(request.getRefundReason());
        return paymentRequest;
    }
}
