package com.linrun.domain.trade.service;

import com.linrun.api.dto.TradeConsistencyCheckRequest;
import com.linrun.api.dto.TradeConsistencyCheckResponse;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TradeConsistencyCheckService {

    public static final String WAIT_GROUP_SETTLEMENT = "WAIT_GROUP_SETTLEMENT";
    public static final String QUOTA_GRANT_REQUIRED = "QUOTA_GRANT_REQUIRED";
    public static final String QUOTA_GRANTED_CONSISTENT = "QUOTA_GRANTED_CONSISTENT";
    public static final String REFUND_ROLLBACK_REQUIRED = "REFUND_ROLLBACK_REQUIRED";
    public static final String TRADE_STATE_CONFLICT = "TRADE_STATE_CONFLICT";

    private final TradeOrderRepository tradeOrderRepository;
    private final UserQuotaRepository userQuotaRepository;

    public TradeConsistencyCheckService(TradeOrderRepository tradeOrderRepository,
                                        UserQuotaRepository userQuotaRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.userQuotaRepository = userQuotaRepository;
    }

    public TradeConsistencyCheckResponse check(TradeConsistencyCheckRequest request) {
        TradeConsistencyCheckRequest safeRequest = request == null ? new TradeConsistencyCheckRequest() : request;
        List<TradeOrderEntity> orders = queryOrders(safeRequest);
        TradeConsistencyCheckResponse response = new TradeConsistencyCheckResponse();
        response.setCheckedCount(orders.size());
        orders.stream().map(this::checkOne).forEach(response.getItems()::add);
        return response;
    }

    private List<TradeOrderEntity> queryOrders(TradeConsistencyCheckRequest request) {
        if (StringUtils.hasText(request.getOrderId())) {
            return List.of(tradeOrderRepository.queryTradeOrderByOrderId(request.getOrderId())
                    .orElseThrow(() -> new AppException("TRADE_0013", "order not found")));
        }
        int pageSize = Math.max(1, Math.min(request.getPageSize() == null ? 20 : request.getPageSize(), 100));
        if (StringUtils.hasText(request.getUserId())) {
            return tradeOrderRepository.queryUserTradeOrders(request.getUserId(), null, pageSize);
        }
        return tradeOrderRepository.queryTradeOrders(null, pageSize, null, null, null);
    }

    private TradeConsistencyCheckResponse.Item checkOne(TradeOrderEntity order) {
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(order.getOrderId()).orElse(null);
        RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(order.getOrderId()).orElse(null);
        boolean grantFlowExists = userQuotaRepository
                .queryFlow(order.getUserId(), UserQuotaService.FLOW_ORDER_GRANT, order.getOrderId())
                .isPresent();
        boolean rollbackFlowExists = userQuotaRepository
                .queryFlow(order.getUserId(), UserQuotaService.FLOW_REFUND_ROLLBACK, order.getOrderId())
                .isPresent();

        TradeConsistencyCheckResponse.Item item = new TradeConsistencyCheckResponse.Item();
        item.setOrderId(order.getOrderId());
        item.setUserId(order.getUserId());
        item.setGoodsId(order.getGoodsId());
        item.setGoodsName(order.getGoodsName());
        item.setActivityId(order.getActivityId());
        item.setBuyType(order.getBuyType() == null ? null : order.getBuyType().name());
        item.setOrderStatus(order.getOrderStatus() == null ? null : order.getOrderStatus().name());
        item.setOriginAmount(order.getOriginAmount());
        item.setOrderPayAmount(order.getPayAmount());
        item.setOrderCreateTime(order.getCreateTime());
        item.setOrderPayTime(order.getPayTime());
        item.setOrderCloseTime(order.getCloseTime());
        if (payOrder != null) {
            item.setPayOrderId(payOrder.getPayOrderId());
            item.setPayChannel(payOrder.getPayChannel());
            item.setPayStatus(payOrder.getPayStatus() == null ? null : payOrder.getPayStatus().name());
            item.setPayAmount(payOrder.getPayAmount());
            item.setOutTradeNo(payOrder.getOutTradeNo());
            item.setPayCreateTime(payOrder.getCreateTime());
            item.setPayTime(payOrder.getPayTime());
        }
        if (refundOrder != null) {
            item.setRefundId(refundOrder.getRefundId());
            item.setRefundStatus(refundOrder.getRefundStatus() == null ? null : refundOrder.getRefundStatus().name());
            item.setRefundAmount(refundOrder.getRefundAmount());
            item.setRefundReason(refundOrder.getRefundReason());
            item.setRefundCreateTime(refundOrder.getCreateTime());
            item.setRefundTime(refundOrder.getRefundTime());
        }
        item.setQuotaGrantFlowExists(grantFlowExists);
        item.setRefundRollbackFlowExists(rollbackFlowExists);
        item.setQuotaGrantAllowed(payOrder != null && !hasStateConflict(order, payOrder) && isQuotaGrantable(order, payOrder));
        fillFacts(order, payOrder, refundOrder, grantFlowExists, rollbackFlowExists, item);
        fillConclusion(order, payOrder, refundOrder, grantFlowExists, rollbackFlowExists, item);
        return item;
    }

    private void fillFacts(TradeOrderEntity order,
                           PayOrderEntity payOrder,
                           RefundOrderEntity refundOrder,
                           boolean grantFlowExists,
                           boolean rollbackFlowExists,
                           TradeConsistencyCheckResponse.Item item) {
        item.getFacts().add("订单：" + value(item.getBuyType()) + "/" + value(item.getOrderStatus()));
        item.getFacts().add("商品：" + value(order.getGoodsName()) + "(" + value(order.getGoodsId()) + ")");
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType())) {
            item.getFacts().add("拼团活动：" + value(order.getActivityId()));
        }
        if (payOrder == null) {
            item.getFacts().add("支付单：缺失");
        } else {
            item.getFacts().add("支付：" + value(item.getPayStatus()) + "/" + value(payOrder.getPayChannel())
                    + "/" + value(payOrder.getPayAmount()));
        }
        if (refundOrder == null) {
            item.getFacts().add("退款单：无");
        } else {
            item.getFacts().add("退款：" + value(item.getRefundStatus()) + "/" + value(refundOrder.getRefundAmount()));
        }
        item.getFacts().add("额度到账流水：" + (grantFlowExists ? "存在" : "缺失"));
        item.getFacts().add("退款回滚流水：" + (rollbackFlowExists ? "存在" : "缺失"));
    }

    private void fillConclusion(TradeOrderEntity order,
                                PayOrderEntity payOrder,
                                RefundOrderEntity refundOrder,
                                boolean grantFlowExists,
                                boolean rollbackFlowExists,
                                TradeConsistencyCheckResponse.Item item) {
        if (payOrder == null || hasStateConflict(order, payOrder)) {
            fillDecision(item,
                    TRADE_STATE_CONFLICT,
                    "order and payment state conflict",
                    "状态冲突",
                    "订单、支付或退款状态不一致，需要按后台事实排查。",
                    false);
            return;
        }
        if (isRefunded(order, payOrder, refundOrder)) {
            if (grantFlowExists && !rollbackFlowExists) {
                fillDecision(item,
                        REFUND_ROLLBACK_REQUIRED,
                        "refund is done but quota rollback flow is missing",
                        "待回滚",
                        "退款已完成，但未找到额度回滚流水。",
                        true);
                return;
            }
            fillDecision(item,
                    QUOTA_GRANTED_CONSISTENT,
                    "refund and quota rollback state are consistent",
                    "退款一致",
                    "退款状态和额度流水一致。",
                    false);
            return;
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType())
                && PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())
                && TradeOrderStatusEnumVO.PAY_SUCCESS.equals(order.getOrderStatus())) {
            fillDecision(item,
                    WAIT_GROUP_SETTLEMENT,
                    "group payment is success, waiting for team settlement",
                    "等待成团",
                    "拼团支付成功只表示名额已支付，暂不能发放额度。",
                    false);
            return;
        }
        if (isQuotaGrantable(order, payOrder) && !grantFlowExists) {
            fillDecision(item,
                    QUOTA_GRANT_REQUIRED,
                    "payment/order is grantable but quota grant flow is missing",
                    "待发放额度",
                    "订单和支付状态已满足发放条件，但未找到额度到账流水。",
                    false);
            return;
        }
        fillDecision(item,
                QUOTA_GRANTED_CONSISTENT,
                "trade and quota states are consistent",
                grantFlowExists ? "额度已到账" : "状态一致",
                grantFlowExists ? "交易状态和额度到账流水一致。" : "订单和支付状态一致，当前不满足额度发放条件。",
                false);
    }

    private void fillDecision(TradeConsistencyCheckResponse.Item item,
                              String conclusion,
                              String message,
                              String settlementLabel,
                              String settlementDetail,
                              boolean refundRollbackRequired) {
        item.setConclusion(conclusion);
        item.setMessage(message);
        item.setSettlementLabel(settlementLabel);
        item.setSettlementDetail(settlementDetail);
        item.setRefundRollbackRequired(refundRollbackRequired);
        if (refundRollbackRequired || TRADE_STATE_CONFLICT.equals(conclusion) || WAIT_GROUP_SETTLEMENT.equals(conclusion)) {
            item.setQuotaGrantAllowed(false);
        }
    }

    private boolean hasStateConflict(TradeOrderEntity order, PayOrderEntity payOrder) {
        if (order == null || payOrder == null || order.getOrderStatus() == null || payOrder.getPayStatus() == null) {
            return true;
        }
        if (PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            return TradeOrderStatusEnumVO.CREATE.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.PAY_WAIT.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.CLOSED.equals(order.getOrderStatus());
        }
        if (PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())) {
            return TradeOrderStatusEnumVO.PAY_SUCCESS.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.GROUP_SETTLED.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.DEAL_DONE.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.REFUNDED.equals(order.getOrderStatus());
        }
        if (PayStatusEnumVO.REFUNDED.equals(payOrder.getPayStatus())) {
            return !TradeOrderStatusEnumVO.REFUNDED.equals(order.getOrderStatus());
        }
        return false;
    }

    private boolean isRefunded(TradeOrderEntity order, PayOrderEntity payOrder, RefundOrderEntity refundOrder) {
        return TradeOrderStatusEnumVO.REFUNDED.equals(order.getOrderStatus())
                || PayStatusEnumVO.REFUNDED.equals(payOrder.getPayStatus())
                || refundOrder != null;
    }

    private boolean isQuotaGrantable(TradeOrderEntity order, PayOrderEntity payOrder) {
        if (!PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            return false;
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType())) {
            return TradeOrderStatusEnumVO.GROUP_SETTLED.equals(order.getOrderStatus())
                    || TradeOrderStatusEnumVO.DEAL_DONE.equals(order.getOrderStatus());
        }
        return TradeOrderStatusEnumVO.PAY_SUCCESS.equals(order.getOrderStatus())
                || TradeOrderStatusEnumVO.DEAL_DONE.equals(order.getOrderStatus());
    }

    private String value(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}














