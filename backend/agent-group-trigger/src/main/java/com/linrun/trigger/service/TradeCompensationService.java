package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.PayStatus;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.model.TradeOrderStatus;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TradeCompensationService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final PaymentService paymentService;
    private final TradeStatusFlowService tradeStatusFlowService;

    public TradeCompensationService(TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    GroupBuyCompensationService groupBuyCompensationService,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    PaymentService paymentService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.paymentService = paymentService;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    @Transactional(rollbackFor = Exception.class)
    public int closeTimeoutUnpaidOrders(LocalDateTime deadline, int limit) {
        List<String> orderIds = tradeOrderRepository.queryTimeoutPayWaitOrderIds(deadline, limit);
        int closedCount = 0;
        for (String orderId : orderIds) {
            if (closeUnpaidOrder(orderId)) {
                closedCount++;
            }
        }
        return closedCount;
    }

    public int refundTimeoutUnsettledGroupOrders(LocalDateTime deadline, int limit) {
        List<String> orderIds = groupBuyOrderLockRepository.queryTimeoutUnsettledPaidOrderIds(deadline, limit);
        int refundCount = 0;
        for (String orderId : orderIds) {
            RefundPaymentRequest request = new RefundPaymentRequest();
            request.setOrderId(orderId);
            request.setRefundReason("group buy timeout unformed");
            paymentService.refund(request);
            refundCount++;
        }
        return refundCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean closeUnpaidOrder(String orderId) {
        TradeOrder tradeOrder = queryTradeOrder(orderId);
        PayOrder payOrder = queryPayOrder(orderId);
        if (TradeOrderStatus.CLOSED.equals(tradeOrder.getOrderStatus())) {
            return true;
        }
        if (!TradeOrderStatus.CREATE.equals(tradeOrder.getOrderStatus())
                && !TradeOrderStatus.PAY_WAIT.equals(tradeOrder.getOrderStatus())) {
            return false;
        }
        if (TradeBuyType.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            CloseUnpaidGroupBuyOrderRequest request = new CloseUnpaidGroupBuyOrderRequest();
            request.setOrderId(orderId);
            request.setCloseTime(LocalDateTime.now());
            groupBuyCompensationService.closeUnpaid(request);
            return true;
        }

        TradeOrderStatus fromOrderStatus = tradeOrder.getOrderStatus();
        PayStatus fromPayStatus = payOrder.getPayStatus();
        tradeOrderService.closeUnpaidOrder(tradeOrder, payOrder, LocalDateTime.now());
        tradeOrderRepository.updateCloseUnpaid(tradeOrder, payOrder);
        recordDirectCloseFlow(tradeOrder, payOrder, fromOrderStatus, fromPayStatus);
        return true;
    }

    public boolean refundOrCloseOrder(String userId, String orderId, String refundReason) {
        TradeOrder tradeOrder = queryTradeOrder(orderId);
        if (StringUtils.hasText(userId) && !userId.equals(tradeOrder.getUserId())) {
            return false;
        }
        if (TradeOrderStatus.REFUNDED.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatus.CLOSED.equals(tradeOrder.getOrderStatus())) {
            return true;
        }
        if (TradeOrderStatus.CREATE.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatus.PAY_WAIT.equals(tradeOrder.getOrderStatus())) {
            return closeUnpaidOrder(orderId);
        }

        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId(orderId);
        request.setRefundReason(refundReason);
        paymentService.refund(request);
        return true;
    }

    private void recordDirectCloseFlow(TradeOrder tradeOrder,
                                       PayOrder payOrder,
                                       TradeOrderStatus fromOrderStatus,
                                       PayStatus fromPayStatus) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_CLOSE_UNPAID,
                fromOrderStatus,
                tradeOrder.getOrderStatus(),
                "direct order closed");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_CLOSE_UNPAID,
                fromPayStatus,
                payOrder.getPayStatus(),
                "direct pay order closed");
    }

    private TradeOrder queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "order not found"));
    }

    private PayOrder queryPayOrder(String orderId) {
        return tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "pay order not found"));
    }
}
