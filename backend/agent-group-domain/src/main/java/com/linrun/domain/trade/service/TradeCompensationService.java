package com.linrun.domain.trade.service;

import com.linrun.api.dto.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.api.dto.PaymentWebhookResponse;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易超时补偿。批量方法对每一笔订单独立处理并吞掉单笔异常：
 * 一笔坏单只记告警，不能让整批补偿中断或回滚，否则定时任务会一直卡在同一笔订单上。
 */
@Service
public class TradeCompensationService {

    private static final System.Logger LOGGER = System.getLogger(TradeCompensationService.class.getName());

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final TradeRefundService tradeRefundService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final PaymentService paymentService;

    public TradeCompensationService(TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    GroupBuyCompensationService groupBuyCompensationService,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    TradeRefundService tradeRefundService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, groupBuyCompensationService, groupBuyOrderLockRepository,
                tradeRefundService, tradeStatusFlowService, null);
    }

    @Autowired
    public TradeCompensationService(TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    GroupBuyCompensationService groupBuyCompensationService,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    TradeRefundService tradeRefundService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    PaymentService paymentService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.tradeRefundService = tradeRefundService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.paymentService = paymentService;
    }

    public int closeTimeoutUnpaidOrders(LocalDateTime deadline, int limit) {
        List<String> orderIds = tradeOrderRepository.queryTimeoutPayWaitOrderIds(deadline, limit);
        int closedCount = 0;
        for (String orderId : orderIds) {
            try {
                if (closeUnpaidOrder(orderId)) {
                    closedCount++;
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "close timeout unpaid order failed, orderId=" + orderId + ", reason=" + e.getMessage());
            }
        }
        return closedCount;
    }

    public int reconcileTimeoutPayWaitOrders(LocalDateTime deadline, int limit) {
        if (paymentService == null) {
            return 0;
        }
        List<String> orderIds = tradeOrderRepository.queryTimeoutPayWaitOrderIds(deadline, limit);
        int completedCount = 0;
        for (String orderId : orderIds) {
            try {
                PaymentWebhookResponse response = paymentService.queryGatewayAndCompleteIfPaid(orderId);
                if (response != null && PayStatusEnumVO.SUCCESS.name().equals(response.getPayStatus())) {
                    completedCount++;
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "reconcile timeout pay-wait order failed, orderId=" + orderId + ", reason=" + e.getMessage());
            }
        }
        return completedCount;
    }

    public int refundTimeoutUnsettledGroupOrders(LocalDateTime deadline, int limit) {
        List<String> orderIds = groupBuyOrderLockRepository.queryTimeoutUnsettledPaidOrderIds(deadline, limit);
        int refundCount = 0;
        for (String orderId : orderIds) {
            try {
                RefundPaymentRequest request = new RefundPaymentRequest();
                request.setOrderId(orderId);
                request.setRefundReason("拼团超时未成团");
                // 定时补偿属于系统发起的退款，固定走系统退款入口，退款原因会带系统标记
                tradeRefundService.refundFromSystem(request);
                refundCount++;
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "refund timeout unsettled group order failed, orderId=" + orderId + ", reason=" + e.getMessage());
            }
        }
        return refundCount;
    }

    public int closeTimeoutUnsettledGroupOrders(LocalDateTime deadline, int limit) {
        List<String> orderIds = groupBuyOrderLockRepository.queryTimeoutUnsettledLockedOrderIds(deadline, limit);
        int closedCount = 0;
        for (String orderId : orderIds) {
            try {
                if (closeUnpaidOrder(orderId)) {
                    closedCount++;
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "close timeout unsettled group order failed, orderId=" + orderId + ", reason=" + e.getMessage());
            }
        }
        return closedCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean closeUnpaidOrder(String orderId) {
        TradeOrderEntity tradeOrder = queryTradeOrder(orderId);
        PayOrderEntity payOrder = queryPayOrder(orderId);
        if (TradeOrderStatusEnumVO.CLOSED.equals(tradeOrder.getOrderStatus())) {
            return true;
        }
        if (!TradeOrderStatusEnumVO.CREATE.equals(tradeOrder.getOrderStatus())
                && !TradeOrderStatusEnumVO.PAY_WAIT.equals(tradeOrder.getOrderStatus())) {
            return false;
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            CloseUnpaidGroupBuyOrderRequest request = new CloseUnpaidGroupBuyOrderRequest();
            request.setOrderId(orderId);
            request.setCloseTime(LocalDateTime.now());
            groupBuyCompensationService.closeUnpaid(request);
            return true;
        }

        TradeOrderStatusEnumVO fromOrderStatus = tradeOrder.getOrderStatus();
        PayStatusEnumVO fromPayStatus = payOrder.getPayStatus();
        tradeOrderService.closeUnpaidOrder(tradeOrder, payOrder, LocalDateTime.now());
        tradeOrderRepository.updateCloseUnpaid(tradeOrder, payOrder);
        recordDirectCloseFlow(tradeOrder, payOrder, fromOrderStatus, fromPayStatus);
        return true;
    }

    public boolean refundOrCloseOrder(String userId, String orderId, String refundReason) {
        TradeOrderEntity tradeOrder = queryTradeOrder(orderId);
        if (StringUtils.hasText(userId) && !userId.equals(tradeOrder.getUserId())) {
            return false;
        }
        if (TradeOrderStatusEnumVO.REFUNDED.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatusEnumVO.CLOSED.equals(tradeOrder.getOrderStatus())) {
            return true;
        }
        if (TradeOrderStatusEnumVO.CREATE.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatusEnumVO.PAY_WAIT.equals(tradeOrder.getOrderStatus())) {
            return closeUnpaidOrder(orderId);
        }

        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId(orderId);
        request.setRefundReason(refundReason);
        tradeRefundService.refund(request);
        return true;
    }

    private void recordDirectCloseFlow(TradeOrderEntity tradeOrder,
                                       PayOrderEntity payOrder,
                                       TradeOrderStatusEnumVO fromOrderStatus,
                                       PayStatusEnumVO fromPayStatus) {
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

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }

    private PayOrderEntity queryPayOrder(String orderId) {
        return tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
    }
}















