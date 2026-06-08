package com.linrun.domain.trade.service;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.api.dto.RefundPaymentResponse;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.domain.groupbuy.service.rules.refund.GroupBuyRefundStrategyRouter;
import com.linrun.domain.groupbuy.service.rules.refund.rule.GroupBuyRefundRuleChain;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeRefundService {

    private final TradeOrderRepository tradeOrderRepository;
    private final PaymentService paymentService;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter;
    private final GroupBuyRefundRuleChain groupBuyRefundRuleChain;
    private final NotifyTaskService notifyTaskService;
    private final UserQuotaService userQuotaService;

    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService) {
        this(tradeOrderRepository, paymentService, groupBuyCompensationService, null, null);
    }

    @Autowired
    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService,
                              NotifyTaskService notifyTaskService,
                              UserQuotaService userQuotaService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.paymentService = paymentService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.notifyTaskService = notifyTaskService;
        this.userQuotaService = userQuotaService;
        this.groupBuyRefundStrategyRouter = new GroupBuyRefundStrategyRouter(paymentService, groupBuyCompensationService);
        this.groupBuyRefundRuleChain = new GroupBuyRefundRuleChain(
                tradeOrderRepository,
                groupBuyCompensationService,
                groupBuyRefundStrategyRouter,
                notifyTaskService);
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

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }
}
