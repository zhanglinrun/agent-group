package com.linrun.domain.market.service.rules.refund;

import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;

public interface GroupBuyRefundStrategy {

    boolean supports(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request);
}















