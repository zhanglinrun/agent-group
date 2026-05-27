package com.linrun.infrastructure.dao;

import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ITradeOrderDao {

    void insertTradeOrder(TradeOrderEntity tradeOrder);

    void insertPayOrder(PayOrderEntity payOrder);

    int updateTradeOrderPaySuccess(TradeOrderEntity tradeOrder);

    int updatePayOrderSuccess(PayOrderEntity payOrder);

    int updateGroupSettledByOrderIds(@Param("orderIds") List<String> orderIds);

    int updateTradeOrderClosed(TradeOrderEntity tradeOrder);

    int updatePayOrderClosed(PayOrderEntity payOrder);

    void insertRefundOrder(RefundOrderEntity refundOrder);

    int updateTradeOrderRefunded(TradeOrderEntity tradeOrder);

    int updatePayOrderRefunded(PayOrderEntity payOrder);

    int updateTradeOrderDealDone(TradeOrderEntity tradeOrder);

    RefundOrderEntity queryRefundOrderByOrderId(@Param("orderId") String orderId);

    TradeOrderEntity queryTradeOrderByOrderId(@Param("orderId") String orderId);

    TradeOrderEntity queryTradeOrderByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    PayOrderEntity queryPayOrderByOrderId(@Param("orderId") String orderId);

    List<TradeOrderEntity> queryUserTradeOrders(@Param("userId") String userId,
                                          @Param("lastId") Long lastId,
                                          @Param("pageSize") int pageSize);

    List<String> queryTimeoutPayWaitOrderIds(@Param("deadline") LocalDateTime deadline,
                                             @Param("limit") int limit);

    TradeOrderEntity queryLatestUnpaidOrder(@Param("userId") String userId,
                                      @Param("goodsId") String goodsId,
                                      @Param("buyType") TradeBuyTypeEnumVO buyType);
}
