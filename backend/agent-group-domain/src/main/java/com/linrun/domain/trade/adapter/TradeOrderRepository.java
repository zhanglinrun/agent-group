package com.linrun.domain.trade.adapter;

import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeOrder;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository {

    void save(TradeOrder tradeOrder, PayOrder payOrder);

    void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder);

    void updateGroupSettledByOrderIds(List<String> orderIds);

    void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder);

    void saveRefundOrder(RefundOrder refundOrder);

    void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder);

    Optional<RefundOrder> queryRefundOrderByOrderId(String orderId);

    Optional<TradeOrder> queryTradeOrderByOrderId(String orderId);

    Optional<PayOrder> queryPayOrderByOrderId(String orderId);
}
