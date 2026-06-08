package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.TradeEventOutboxRepository;
import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import com.linrun.infrastructure.trade.converter.TradePOConverter;
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
        tradeEventOutboxDao.insert(TradePOConverter.toPO(outbox));
    }

    @Override
    public List<TradeEventOutboxEntity> queryPending(int limit) {
        return TradePOConverter.toTradeEventOutboxEntities(tradeEventOutboxDao.queryPending(limit));
    }

    @Override
    public long countByStatus(int sendStatus) {
        return tradeEventOutboxDao.countByStatus(sendStatus);
    }

    @Override
    public List<TradeEventOutboxEntity> queryRecentByStatus(int sendStatus, int limit) {
        return TradePOConverter.toTradeEventOutboxEntities(
                tradeEventOutboxDao.queryRecentByStatus(sendStatus, Math.max(1, limit)));
    }

    @Override
    public int updateStatusProcessing(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusProcessing(TradePOConverter.toPO(outbox));
    }

    @Override
    public int updateStatusSuccess(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusSuccess(TradePOConverter.toPO(outbox));
    }

    @Override
    public int updateStatusRetry(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusRetry(TradePOConverter.toPO(outbox));
    }

    @Override
    public int updateStatusDeadLetter(TradeEventOutboxEntity outbox) {
        return tradeEventOutboxDao.updateStatusDeadLetter(TradePOConverter.toPO(outbox));
    }
}
