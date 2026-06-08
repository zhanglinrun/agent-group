package com.linrun.domain.groupbuy.service.rules.refund;

import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.trade.service.payment.PaymentService;

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
