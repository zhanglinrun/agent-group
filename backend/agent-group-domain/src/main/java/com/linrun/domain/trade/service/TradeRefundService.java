package com.linrun.domain.trade.service;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.QueryPaymentRefundRequest;
import com.linrun.api.dto.QueryPaymentRefundResponse;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.api.dto.RefundPaymentResponse;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.domain.groupbuy.service.rules.refund.rule.GroupBuyRefundRuleChain;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TradeRefundService {

    private final TradeOrderRepository tradeOrderRepository;
    private final PaymentService paymentService;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final GroupBuyRefundRuleChain groupBuyRefundRuleChain;
    private final NotifyTaskService notifyTaskService;
    private final UserQuotaService userQuotaService;

    @Autowired
    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService,
                              GroupBuyRefundRuleChain groupBuyRefundRuleChain,
                              NotifyTaskService notifyTaskService,
                              UserQuotaService userQuotaService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.paymentService = paymentService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.groupBuyRefundRuleChain = groupBuyRefundRuleChain;
        this.notifyTaskService = notifyTaskService;
        this.userQuotaService = userQuotaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refund(RefundPaymentRequest request) {
        RefundPaymentResponse response = paymentService.refund(request);
        rollbackQuotaIfAvailable(response.getOrderId());
        releaseGroupBuyIfNeeded(response.getOrderId(), request == null ? null : request.getRefundReason());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refundFromSystem(RefundPaymentRequest request) {
        RefundPaymentResponse response = paymentService.refundFromSystem(request);
        rollbackQuotaIfAvailable(response.getOrderId());
        releaseGroupBuyIfNeeded(response.getOrderId(), request == null ? null : request.getRefundReason());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse refundGroupBuy(RefundGroupBuyOrderRequest request) {
        GroupBuyCompensationResponse response = groupBuyRefundRuleChain.refund(request);
        rollbackQuotaIfAvailable(request == null ? null : request.getOrderId());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public QueryPaymentRefundResponse queryRefund(QueryPaymentRefundRequest request) {
        QueryPaymentRefundResponse response = paymentService.queryRefund(request);
        rollbackQuotaIfRefundConfirmed(response);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public QueryPaymentRefundResponse handleRefundWebhook(PaymentWebhookRequest request) {
        QueryPaymentRefundResponse response = paymentService.handleRefundWebhook(request);
        rollbackQuotaIfRefundConfirmed(response);
        return response;
    }

    private void releaseGroupBuyIfNeeded(String orderId, String refundReason) {
        TradeOrderEntity tradeOrder = queryTradeOrder(orderId);
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return;
        }
        RefundGroupBuyOrderRequest groupRequest = new RefundGroupBuyOrderRequest();
        groupRequest.setOrderId(orderId);
        groupRequest.setRefundReason(refundReason);
        GroupBuyCompensationResponse response = groupBuyCompensationService.releaseRefundedOrder(groupRequest);
        if (notifyTaskService != null) {
            notifyTaskService.createGroupRefundTask(response);
        }
    }

    private void rollbackQuotaIfAvailable(String orderId) {
        if (userQuotaService != null) {
            userQuotaService.rollbackQuotaForRefundedOrder(queryTradeOrder(orderId));
        }
    }

    private void rollbackQuotaIfRefundConfirmed(QueryPaymentRefundResponse response) {
        if (response == null || !response.isVerified() || !isRefundSuccessStatus(response.getRefundStatus())) {
            return;
        }
        String orderId = response.getOrderId();
        rollbackQuotaIfAvailable(orderId);
        releaseGroupBuyIfNeeded(orderId, "网关退款结果确认");
    }

    private boolean isRefundSuccessStatus(String refundStatus) {
        if (!StringUtils.hasText(refundStatus)) {
            return false;
        }
        String normalized = refundStatus.trim().toUpperCase();
        return "SUCCESS".equals(normalized)
                || "REFUND_SUCCESS".equals(normalized)
                || "REFUNDED".equals(normalized);
    }

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }
}













