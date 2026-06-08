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
        item.setBuyType(order.getBuyType() == null ? null : order.getBuyType().name());
        item.setOrderStatus(order.getOrderStatus() == null ? null : order.getOrderStatus().name());
        if (payOrder != null) {
            item.setPayOrderId(payOrder.getPayOrderId());
            item.setPayChannel(payOrder.getPayChannel());
            item.setPayStatus(payOrder.getPayStatus() == null ? null : payOrder.getPayStatus().name());
            item.setPayAmount(payOrder.getPayAmount());
        }
        if (refundOrder != null) {
            item.setRefundId(refundOrder.getRefundId());
            item.setRefundStatus(refundOrder.getRefundStatus() == null ? null : refundOrder.getRefundStatus().name());
        }
        item.setQuotaGrantFlowExists(grantFlowExists);
        item.setRefundRollbackFlowExists(rollbackFlowExists);
        fillConclusion(order, payOrder, refundOrder, grantFlowExists, rollbackFlowExists, item);
        return item;
    }

    private void fillConclusion(TradeOrderEntity order,
                                PayOrderEntity payOrder,
                                RefundOrderEntity refundOrder,
                                boolean grantFlowExists,
                                boolean rollbackFlowExists,
                                TradeConsistencyCheckResponse.Item item) {
        if (payOrder == null || hasStateConflict(order, payOrder)) {
            item.setConclusion(TRADE_STATE_CONFLICT);
            item.setMessage("order and payment state conflict");
            return;
        }
        if (isRefunded(order, payOrder, refundOrder)) {
            if (grantFlowExists && !rollbackFlowExists) {
                item.setConclusion(REFUND_ROLLBACK_REQUIRED);
                item.setMessage("refund is done but quota rollback flow is missing");
                return;
            }
            item.setConclusion(QUOTA_GRANTED_CONSISTENT);
            item.setMessage("refund and quota rollback state are consistent");
            return;
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType())
                && PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())
                && TradeOrderStatusEnumVO.PAY_SUCCESS.equals(order.getOrderStatus())) {
            item.setConclusion(WAIT_GROUP_SETTLEMENT);
            item.setMessage("group payment is success, waiting for team settlement");
            return;
        }
        if (isQuotaGrantable(order, payOrder) && !grantFlowExists) {
            item.setConclusion(QUOTA_GRANT_REQUIRED);
            item.setMessage("payment/order is grantable but quota grant flow is missing");
            return;
        }
        item.setConclusion(QUOTA_GRANTED_CONSISTENT);
        item.setMessage("trade and quota states are consistent");
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
}
