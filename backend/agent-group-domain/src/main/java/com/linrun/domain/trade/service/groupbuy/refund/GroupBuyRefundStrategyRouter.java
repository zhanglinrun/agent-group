package com.linrun.domain.trade.service.groupbuy.refund;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.service.GroupBuyCompensationService;
import com.linrun.domain.trade.service.payment.PaymentService;
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
                .orElseThrow(() -> new AppException("TRADE_0020", "当前拼团订单状态不支持退款"))
                .refund(request);
    }
}
