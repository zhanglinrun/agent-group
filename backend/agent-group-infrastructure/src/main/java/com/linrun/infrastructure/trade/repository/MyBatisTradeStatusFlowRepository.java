package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.infrastructure.trade.converter.TradePOConverter;
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
        tradeStatusFlowDao.insert(TradePOConverter.toPO(flow));
    }

    @Override
    public List<TradeStatusFlowEntity> queryByOrderId(String orderId) {
        return TradePOConverter.toTradeStatusFlowEntities(tradeStatusFlowDao.queryByOrderId(orderId));
    }
}















