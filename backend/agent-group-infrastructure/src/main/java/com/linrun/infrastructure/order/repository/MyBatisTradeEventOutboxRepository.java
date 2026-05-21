package com.linrun.infrastructure.order.repository;

import com.linrun.domain.order.adapter.TradeEventOutboxRepository;
import com.linrun.domain.order.model.entity.TradeEventOutboxEntity;
import com.linrun.infrastructure.dao.ITradeEventOutboxDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisTradeEventOutboxRepository implements TradeEventOutboxRepository {

    private final ITradeEventOutboxDao tradeEventOutboxDao;

    public MyBatisTradeEventOutboxRepository(ITradeEventOutboxDao tradeEventOutboxDao) {
        this.tradeEventOutboxDao = tradeEventOutboxDao;
    }

    @Override
    public void save(TradeEventOutboxEntity outbox) {
        tradeEventOutboxDao.insert(outbox);
    }

    @Override
    public List<TradeEventOutboxEntity> queryPending(int limit) {
        return tradeEventOutboxDao.queryPending(limit);
    }

    @Override
    public int updateStatusProcessing(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusProcessing(outbox);
    }

    @Override
    public int updateStatusSuccess(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusSuccess(outbox);
    }

    @Override
    public int updateStatusRetry(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusRetry(outbox);
    }

    @Override
    public int updateStatusDeadLetter(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusDeadLetter(outbox);
    }
}
