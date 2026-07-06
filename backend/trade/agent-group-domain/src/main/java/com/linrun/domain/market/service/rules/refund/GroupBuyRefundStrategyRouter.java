package com.linrun.domain.market.service.rules.refund;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupBuyRefundStrategyRouter {

    private final List<GroupBuyRefundStrategy> strategies;

    public GroupBuyRefundStrategyRouter(List<GroupBuyRefundStrategy> strategies) {
        this.strategies = strategies;
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















