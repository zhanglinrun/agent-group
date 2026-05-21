package com.linrun.infrastructure.order.repository;

import com.linrun.domain.order.adapter.TradeStatusFlowRepository;
import com.linrun.domain.order.model.entity.TradeStatusFlowEntity;
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
    public void save(TradeStatusFlowEntity flow) {
        tradeStatusFlowDao.insert(flow);
    }

    @Override
    public List<TradeStatusFlowEntity> queryByOrderId(String orderId) {
        return tradeStatusFlowDao.queryByOrderId(orderId);
    }
}
