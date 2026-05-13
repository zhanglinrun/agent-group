package com.linrun.infrastructure.dao;

import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ITradeOrderDao {

    void insertTradeOrder(TradeOrder tradeOrder);

    void insertPayOrder(PayOrder payOrder);

    TradeOrder queryTradeOrderByOrderId(@Param("orderId") String orderId);

    PayOrder queryPayOrderByOrderId(@Param("orderId") String orderId);
}
