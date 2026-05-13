package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.TradeStatusFlow;
import com.linrun.infrastructure.dao.ITradeStatusFlowDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisTradeStatusFlowRepository implements TradeStatusFlowRepository {

    private final ITradeStatusFlowDao tradeStatusFlowDao;

    public MyBatisTradeStatusFlowRepository(ITradeStatusFlowDao tradeStatusFlowDao) {
        this.tradeStatusFlowDao = tradeStatusFlowDao;
    }

    @Override
    public void save(TradeStatusFlow flow) {
        tradeStatusFlowDao.insert(flow);
    }

    @Override
    public List<TradeStatusFlow> queryByOrderId(String orderId) {
        return tradeStatusFlowDao.queryByOrderId(orderId);
    }
}
