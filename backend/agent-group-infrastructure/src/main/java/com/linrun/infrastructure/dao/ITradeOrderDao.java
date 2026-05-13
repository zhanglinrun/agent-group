package com.linrun.infrastructure.dao;

import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeOrderDao {

    void insertTradeOrder(TradeOrder tradeOrder);

    void insertPayOrder(PayOrder payOrder);

    int updateTradeOrderPaySuccess(TradeOrder tradeOrder);

    int updatePayOrderSuccess(PayOrder payOrder);

    int updateGroupSettledByOrderIds(@Param("orderIds") List<String> orderIds);

    int updateTradeOrderClosed(TradeOrder tradeOrder);

    int updatePayOrderClosed(PayOrder payOrder);

    void insertRefundOrder(RefundOrder refundOrder);

    int updateTradeOrderRefunded(TradeOrder tradeOrder);

    int updatePayOrderRefunded(PayOrder payOrder);

    RefundOrder queryRefundOrderByOrderId(@Param("orderId") String orderId);

    TradeOrder queryTradeOrderByOrderId(@Param("orderId") String orderId);

    PayOrder queryPayOrderByOrderId(@Param("orderId") String orderId);
}
