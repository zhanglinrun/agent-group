package com.linrun.domain.market.service.rules.refund;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.market.service.GroupBuyCompensationService;
import com.linrun.domain.trade.service.payment.PaymentService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
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















