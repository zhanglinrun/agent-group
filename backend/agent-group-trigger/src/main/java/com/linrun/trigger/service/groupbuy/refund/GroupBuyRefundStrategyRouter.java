package com.linrun.trigger.service.groupbuy.refund;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.trigger.service.GroupBuyCompensationService;
import com.linrun.trigger.service.PaymentService;
import com.linrun.types.exception.AppException;

import java.util.List;

public class GroupBuyRefundStrategyRouter {

    private final List<GroupBuyRefundStrategy> strategies;

    public GroupBuyRefundStrategyRouter(PaymentService paymentService,
                                        GroupBuyCompensationService groupBuyCompensationService) {
        this.strategies = List.of(
                new UnpaidGroupBuyRefundStrategy(groupBuyCompensationService),
                new PaidUnsettledGroupBuyRefundStrategy(paymentService, groupBuyCompensationService),
                new PaidSettledGroupBuyRefundStrategy(paymentService, groupBuyCompensationService)
        );
    }

    public GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request,
                                               TradeOrderEntity tradeOrder,
                                               PayOrderEntity payOrder) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(tradeOrder, payOrder))
                .findFirst()
                .orElseThrow(() -> new AppException("TRADE_0020", "unsupported group buy refund status"))
                .refund(request);
    }
}
