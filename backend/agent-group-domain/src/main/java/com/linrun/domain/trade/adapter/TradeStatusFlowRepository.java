package com.linrun.domain.trade.adapter;

import com.linrun.domain.trade.model.TradeStatusFlow;

import java.util.List;

public interface TradeStatusFlowRepository {

    void save(TradeStatusFlow flow);

    List<TradeStatusFlow> queryByOrderId(String orderId);
}
