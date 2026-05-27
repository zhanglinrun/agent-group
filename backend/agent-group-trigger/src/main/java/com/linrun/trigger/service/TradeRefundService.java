package com.linrun.trigger.service;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.api.payment.response.RefundPaymentResponse;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.trigger.service.groupbuy.refund.GroupBuyRefundStrategyRouter;
import com.linrun.trigger.service.groupbuy.refund.rule.GroupBuyRefundRuleChain;
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

    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService) {
        this(tradeOrderRepository, paymentService, groupBuyCompensationService, null);
    }

    @Autowired
    public TradeRefundService(TradeOrderRepository tradeOrderRepository,
                              PaymentService paymentService,
                              GroupBuyCompensationService groupBuyCompensationService,
                              NotifyTaskService notifyTaskService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.paymentService = paymentService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.notifyTaskService = notifyTaskService;
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
        releaseGroupBuyIfNeeded(response.getOrderId(), request == null ? null : request.getRefundReason());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse refundGroupBuy(RefundGroupBuyOrderRequest request) {
        return groupBuyRefundRuleChain.refund(request);
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

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "order not found"));
    }
}
