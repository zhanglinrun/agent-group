package com.linrun.infrastructure.dao;

import com.linrun.domain.trade.model.TradeStatusFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeStatusFlowDao {

    void insert(TradeStatusFlow flow);

    List<TradeStatusFlow> queryByOrderId(@Param("orderId") String orderId);
}
