package com.linrun.trigger.service.groupbuy.refund;

import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.trigger.service.GroupBuyCompensationService;
import com.linrun.trigger.service.PaymentService;

public class PaidSettledGroupBuyRefundStrategy extends PaidUnsettledGroupBuyRefundStrategy {

    public PaidSettledGroupBuyRefundStrategy(PaymentService paymentService,
                                             GroupBuyCompensationService groupBuyCompensationService) {
        super(paymentService, groupBuyCompensationService);
    }

    @Override
    public boolean supports(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        return PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())
                && (TradeOrderStatusEnumVO.GROUP_SETTLED.equals(tradeOrder.getOrderStatus())
                || TradeOrderStatusEnumVO.DEAL_DONE.equals(tradeOrder.getOrderStatus()));
    }
}
