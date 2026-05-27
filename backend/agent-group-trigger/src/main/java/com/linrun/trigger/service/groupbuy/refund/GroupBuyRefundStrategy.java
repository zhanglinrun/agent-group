package com.linrun.trigger.service.groupbuy.refund;

import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;

public interface GroupBuyRefundStrategy {

    boolean supports(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request);
}
