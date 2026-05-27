package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;

import java.util.List;

public interface TradeStatusFlowRepository {

    void save(TradeStatusFlowEntity flow);

    List<TradeStatusFlowEntity> queryByOrderId(String orderId);
}
