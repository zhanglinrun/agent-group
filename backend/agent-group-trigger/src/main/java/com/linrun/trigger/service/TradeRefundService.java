package com.linrun.trigger.service;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.api.payment.response.RefundPaymentResponse;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TradeRefundService {

    private final TradeOrderRepository tradeOrderRepository;
    private final PaymentService paymentService;
    private final GroupBuyCompensationService groupBuyCompensationService;

    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.paymentService = paymentService;
        this.groupBuyCompensationService = groupBuyCompensationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refund(RefundPaymentRequest request) {
        RefundPaymentResponse response = paymentService.refund(request);
        releaseGroupBuyIfNeeded(response.getOrderId(), request == null ? null : request.getRefundReason());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse refundGroupBuy(RefundGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        validateGroupBuyOrder(tradeOrder);

        RefundPaymentRequest paymentRequest = new RefundPaymentRequest();
        paymentRequest.setOrderId(request.getOrderId());
        paymentRequest.setRefundReason(request.getRefundReason());
        paymentService.refund(paymentRequest);
        return groupBuyCompensationService.releaseRefundedOrder(request);
    }

    private void releaseGroupBuyIfNeeded(String orderId, String refundReason) {
        TradeOrderEntity tradeOrder = queryTradeOrder(orderId);
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return;
        }
        RefundGroupBuyOrderRequest groupRequest = new RefundGroupBuyOrderRequest();
        groupRequest.setOrderId(orderId);
        groupRequest.setRefundReason(refundReason);
        groupBuyCompensationService.releaseRefundedOrder(groupRequest);
    }

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }

    private void validateGroupBuyOrder(TradeOrderEntity tradeOrder) {
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            throw new AppException("TRADE_0008", "非拼团订单不能做拼团补偿");
        }
    }
}
