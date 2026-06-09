package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.infrastructure.trade.converter.TradePOConverter;
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
        tradeEventConsumeRecordDao.insert(TradePOConverter.toPO(record));
    }

    @Override
    public Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId) {
        return Optional.ofNullable(TradePOConverter.toEntity(tradeEventConsumeRecordDao.queryByEventId(eventId)));
    }

    @Override
    public int updateStatusProcessing(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusProcessing(TradePOConverter.toPO(record));
    }

    @Override
    public int updateStatusConsumed(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusConsumed(TradePOConverter.toPO(record));
    }

    @Override
    public int updateStatusRetry(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusRetry(TradePOConverter.toPO(record));
    }

    @Override
    public int updateStatusDeadLetter(TradeEventConsumeRecordEntity record) {
        return tradeEventConsumeRecordDao.updateStatusDeadLetter(TradePOConverter.toPO(record));
    }
}















