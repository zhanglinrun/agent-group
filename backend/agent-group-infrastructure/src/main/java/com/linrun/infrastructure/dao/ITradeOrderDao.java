package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.PayOrderPO;
import com.linrun.infrastructure.po.RefundOrderPO;
import com.linrun.infrastructure.po.TradeOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ITradeOrderDao {

    void insertTradeOrder(TradeOrderPO tradeOrder);

    void insertPayOrder(PayOrderPO payOrder);

    int updateTradeOrderPaySuccess(TradeOrderPO tradeOrder);

    int updatePayOrderSuccess(PayOrderPO payOrder);

    int updateGroupSettledByOrderIds(@Param("orderIds") List<String> orderIds);

    int updateTradeOrderClosed(TradeOrderPO tradeOrder);

    int updatePayOrderClosed(PayOrderPO payOrder);

    void insertRefundOrder(RefundOrderPO refundOrder);

    int updateTradeOrderRefunded(TradeOrderPO tradeOrder);

    int updatePayOrderRefunded(PayOrderPO payOrder);

    int updateTradeOrderDealDone(TradeOrderPO tradeOrder);

    RefundOrderPO queryRefundOrderByOrderId(@Param("orderId") String orderId);

    TradeOrderPO queryTradeOrderByOrderId(@Param("orderId") String orderId);

    TradeOrderPO queryTradeOrderByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    PayOrderPO queryPayOrderByOrderId(@Param("orderId") String orderId);

    List<TradeOrderPO> queryUserTradeOrders(@Param("userId") String userId,
                                            @Param("lastId") Long lastId,
                                            @Param("pageSize") int pageSize);

    List<TradeOrderPO> queryUserTradeOrdersFiltered(@Param("userId") String userId,
                                                    @Param("lastId") Long lastId,
                                                    @Param("pageSize") int pageSize,
                                                    @Param("buyType") String buyType,
                                                    @Param("orderStatus") String orderStatus,
                                                    @Param("keyword") String keyword);

    List<RefundOrderPO> queryRefundOrders(@Param("userId") String userId,
                                          @Param("refundStatus") String refundStatus,
                                          @Param("pageSize") int pageSize);

    List<String> queryTimeoutPayWaitOrderIds(@Param("deadline") LocalDateTime deadline,
                                             @Param("limit") int limit);

    TradeOrderPO queryLatestUnpaidOrder(@Param("userId") String userId,
                                        @Param("goodsId") String goodsId,
                                        @Param("buyType") String buyType);
}
