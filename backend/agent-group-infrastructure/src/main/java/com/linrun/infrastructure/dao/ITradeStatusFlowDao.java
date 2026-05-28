package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.TradeStatusFlowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeStatusFlowDao {

    void insert(TradeStatusFlowPO flow);

    List<TradeStatusFlowPO> queryByOrderId(@Param("orderId") String orderId);
}
