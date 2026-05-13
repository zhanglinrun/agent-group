package com.linrun.trigger.service;

import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.api.trade.response.MockPayCallbackResponse;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.service.TradeOrderService;
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

    public MockPayCallbackService(TradeOrderRepository tradeOrderRepository,
                                  TradeOrderService tradeOrderService,
                                  GroupBuySettlementService groupBuySettlementService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuySettlementService = groupBuySettlementService;
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

        LocalDateTime payTime = request.getPayTime() == null ? LocalDateTime.now() : request.getPayTime();
        tradeOrderService.markPaySuccess(tradeOrder, payOrder, request.getOutTradeNo(), payTime);
        tradeOrderRepository.updatePaySuccess(tradeOrder, payOrder);
        groupBuySettlementService.settlePaySuccess(tradeOrder);

        return toResponse(tradeOrder, payOrder);
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
