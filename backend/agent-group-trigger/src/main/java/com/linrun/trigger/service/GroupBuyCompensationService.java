package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.groupbuy.request.RefundGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.GroupBuyCompensationResponse;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class GroupBuyCompensationService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_REFUND_REASON = "拼团未成团自动退款";

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;

    public GroupBuyCompensationService(TradeOrderRepository tradeOrderRepository,
                                       TradeOrderService tradeOrderService,
                                       GroupBuyOrderLockRepository groupBuyOrderLockRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse closeUnpaid(CloseUnpaidGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrder tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrder payOrder = queryPayOrder(request.getOrderId());
        validateGroupBuyOrder(tradeOrder);

        LocalDateTime closeTime = request.getCloseTime() == null ? LocalDateTime.now() : request.getCloseTime();
        tradeOrderService.closeUnpaidOrder(tradeOrder, payOrder, closeTime);
        tradeOrderRepository.updateCloseUnpaid(tradeOrder, payOrder);
        GroupBuySettlementResult releaseResult = groupBuyOrderLockRepository.releaseLockedOrder(tradeOrder.getOrderId());
        return toResponse(tradeOrder, payOrder, null, releaseResult, closeTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse refundUnsettled(RefundGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrder tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrder payOrder = queryPayOrder(request.getOrderId());
        validateGroupBuyOrder(tradeOrder);

        RefundOrder existed = tradeOrderRepository.queryRefundOrderByOrderId(tradeOrder.getOrderId()).orElse(null);
        if (existed != null) {
            GroupBuySettlementResult releaseResult = groupBuyOrderLockRepository.releasePaidOrder(tradeOrder.getOrderId());
            return toResponse(tradeOrder, payOrder, existed, releaseResult, existed.getRefundTime());
        }

        LocalDateTime refundTime = request.getRefundTime() == null ? LocalDateTime.now() : request.getRefundTime();
        RefundOrder refundOrder = RefundOrder.success(
                nextNo("R"),
                tradeOrder,
                payOrder,
                resolveRefundReason(request),
                refundTime);
        tradeOrderService.refundPaidOrder(tradeOrder, payOrder);
        tradeOrderRepository.saveRefundOrder(refundOrder);
        tradeOrderRepository.updateRefunded(tradeOrder, payOrder);
        GroupBuySettlementResult releaseResult = groupBuyOrderLockRepository.releasePaidOrder(tradeOrder.getOrderId());
        return toResponse(tradeOrder, payOrder, refundOrder, releaseResult, refundTime);
    }

    private void validateGroupBuyOrder(TradeOrder tradeOrder) {
        if (!TradeBuyType.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            throw new AppException("TRADE_0008", "非拼团订单不能做拼团补偿");
        }
    }

    private TradeOrder queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }

    private PayOrder queryPayOrder(String orderId) {
        return tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
    }

    private String resolveRefundReason(RefundGroupBuyOrderRequest request) {
        return StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : DEFAULT_REFUND_REASON;
    }

    private GroupBuyCompensationResponse toResponse(TradeOrder tradeOrder,
                                                   PayOrder payOrder,
                                                   RefundOrder refundOrder,
                                                   GroupBuySettlementResult releaseResult,
                                                   LocalDateTime finishTime) {
        GroupBuyCompensationResponse response = new GroupBuyCompensationResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setRefundId(refundOrder == null ? null : refundOrder.getRefundId());
        response.setTeamId(releaseResult.getTeam().getTeamId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setLockStatus(releaseResult.getOrderLock().getLockStatus().name());
        response.setTeamStatus(releaseResult.getTeam().getTeamStatus().name());
        response.setLockedCount(releaseResult.getTeam().getLockCount());
        response.setCompleteCount(releaseResult.getTeam().getCompleteCount());
        response.setRefundAmount(refundOrder == null ? null : refundOrder.getRefundAmount());
        response.setFinishTime(finishTime);
        return response;
    }

    private String nextNo(String prefix) {
        String timePart = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + timePart + randomPart;
    }
}
