package com.linrun.domain.order.adapter;

import com.linrun.domain.order.model.TradeStatusFlow;

import java.util.List;

public interface TradeStatusFlowRepository {

    void save(TradeStatusFlow flow);

    List<TradeStatusFlow> queryByOrderId(String orderId);
}
