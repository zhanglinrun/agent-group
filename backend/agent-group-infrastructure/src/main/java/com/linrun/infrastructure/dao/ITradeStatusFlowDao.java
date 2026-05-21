package com.linrun.infrastructure.dao;

import com.linrun.domain.order.model.entity.TradeStatusFlowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeStatusFlowDao {

    void insert(TradeStatusFlowEntity flow);

    List<TradeStatusFlowEntity> queryByOrderId(@Param("orderId") String orderId);
}
