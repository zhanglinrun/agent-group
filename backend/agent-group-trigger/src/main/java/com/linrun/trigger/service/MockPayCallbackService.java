package com.linrun.trigger.service;

import com.linrun.api.order.request.MockPayCallbackRequest;
import com.linrun.api.order.response.MockPayCallbackResponse;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.PayOrder;
import com.linrun.domain.order.model.PayStatus;
import com.linrun.domain.order.model.TradeOrder;
import com.linrun.domain.order.model.TradeOrderStatus;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class MockPayCallbackService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuySettlementService groupBuySettlementService;
    private final TradeStatusFlowService tradeStatusFlowService;

    public MockPayCallbackService(TradeOrderRepository tradeOrderRepository,
                                  TradeOrderService tradeOrderService,
                                  GroupBuySettlementService groupBuySettlementService,
                                  TradeStatusFlowService tradeStatusFlowService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuySettlementService = groupBuySettlementService;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MockPayCallbackResponse paySuccess(MockPayCallbackRequest request) {
        if (request == null) {
            throw new AppException("0001", "支付回调参数不能为空");
        }
        if (!StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        if (!StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "外部交易单号不能为空");
        }

        TradeOrder tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(request.getOrderId())
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrder payOrder = tradeOrderRepository.queryPayOrderByOrderId(request.getOrderId())
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));

        TradeOrderStatus fromOrderStatus = tradeOrder.getOrderStatus();
        PayStatus fromPayStatus = payOrder.getPayStatus();
        LocalDateTime payTime = request.getPayTime() == null ? LocalDateTime.now() : request.getPayTime();
        tradeOrderService.markPaySuccess(tradeOrder, payOrder, request.getOutTradeNo(), payTime);
        tradeOrderRepository.updatePaySuccess(tradeOrder, payOrder);
        recordPaySuccessFlow(tradeOrder, payOrder, fromOrderStatus, fromPayStatus);
        groupBuySettlementService.settlePaySuccess(tradeOrder);

        return toResponse(tradeOrder, payOrder);
    }

    private void recordPaySuccessFlow(TradeOrder tradeOrder,
                                      PayOrder payOrder,
                                      TradeOrderStatus fromOrderStatus,
                                      PayStatus fromPayStatus) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                fromOrderStatus,
                tradeOrder.getOrderStatus(),
                "order paid");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                fromPayStatus,
                payOrder.getPayStatus(),
                "pay success");
    }

    private MockPayCallbackResponse toResponse(TradeOrder tradeOrder, PayOrder payOrder) {
        MockPayCallbackResponse response = new MockPayCallbackResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setOutTradeNo(payOrder.getOutTradeNo());
        response.setPayTime(payOrder.getPayTime());
        return response;
    }
}
