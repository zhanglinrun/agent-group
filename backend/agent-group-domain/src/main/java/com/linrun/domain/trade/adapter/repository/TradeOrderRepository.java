package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository {

    void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    void updateGroupSettledByOrderIds(List<String> orderIds);

    void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    void saveRefundOrder(RefundOrderEntity refundOrder);

    void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder);

    default void updateDealDone(TradeOrderEntity tradeOrder) {
    }

    Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId);

    Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId);

    Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId);

    default Optional<TradeOrderEntity> queryTradeOrderByIdempotentKey(String idempotentKey) {
        return Optional.empty();
    }

    default List<TradeOrderEntity> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
        return List.of();
    }

    default List<TradeOrderEntity> queryUserTradeOrders(String userId,
                                                        Long lastId,
                                                        int pageSize,
                                                        Integer marketType,
                                                        String orderStatus,
                                                        String keyword) {
        return queryUserTradeOrders(userId, lastId, pageSize);
    }

    default List<TradeOrderEntity> queryTradeOrders(Long lastId,
                                                    int pageSize,
                                                    Integer marketType,
                                                    String orderStatus,
                                                    String keyword) {
        return List.of();
    }

    default List<RefundOrderEntity> queryRefundOrders(String userId, String refundStatus, int pageSize) {
        return List.of();
    }

    default List<String> queryTimeoutPayWaitOrderIds(LocalDateTime deadline, int limit) {
        return List.of();
    }

    default Optional<TradeOrderEntity> queryLatestUnpaidOrder(String userId, String goodsId, TradeBuyTypeEnumVO buyType) {
        return Optional.empty();
    }
}
