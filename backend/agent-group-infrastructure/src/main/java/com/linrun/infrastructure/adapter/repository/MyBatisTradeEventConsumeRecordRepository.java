package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.infrastructure.dao.ITradeEventConsumeRecordDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisTradeEventConsumeRecordRepository implements TradeEventConsumeRecordRepository {

    private final ITradeEventConsumeRecordDao tradeEventConsumeRecordDao;

    public MyBatisTradeEventConsumeRecordRepository(ITradeEventConsumeRecordDao tradeEventConsumeRecordDao) {
        this.tradeEventConsumeRecordDao = tradeEventConsumeRecordDao;
    }

    @Override
    public void save(TradeEventConsumeRecordEntity record) {
        tradeEventConsumeRecordDao.insert(record);
    }

    @Override
    public Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId) {
        return Optional.ofNullable(tradeEventConsumeRecordDao.queryByEventId(eventId));
    }

    @Override
    public int updateStatusProcessing(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusProcessing(record);
    }

    @Override
    public int updateStatusConsumed(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusConsumed(record);
    }

    @Override
    public int updateStatusRetry(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusRetry(record);
    }

    @Override
    public int updateStatusDeadLetter(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusDeadLetter(record);
    }
}
