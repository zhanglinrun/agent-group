package com.linrun.domain.order.adapter;

import com.linrun.domain.order.model.entity.TradeStatusFlowEntity;

import java.util.List;

public interface TradeStatusFlowRepository {

    void save(TradeStatusFlowEntity flow);

    List<TradeStatusFlowEntity> queryByOrderId(String orderId);
}
