package com.linrun.domain.trade.adapter;

import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository {

    void save(TradeOrder tradeOrder, PayOrder payOrder);

    void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder);

    void updateGroupSettledByOrderIds(List<String> orderIds);

    void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder);

    void saveRefundOrder(RefundOrder refundOrder);

    void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder);

    default void updateDealDone(TradeOrder tradeOrder) {
    }

    Optional<RefundOrder> queryRefundOrderByOrderId(String orderId);

    Optional<TradeOrder> queryTradeOrderByOrderId(String orderId);

    Optional<PayOrder> queryPayOrderByOrderId(String orderId);

    default List<TradeOrder> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
        return List.of();
    }

    default List<String> queryTimeoutPayWaitOrderIds(LocalDateTime deadline, int limit) {
        return List.of();
    }

    default Optional<TradeOrder> queryLatestUnpaidOrder(String userId, String goodsId, TradeBuyType buyType) {
        return Optional.empty();
    }
}
