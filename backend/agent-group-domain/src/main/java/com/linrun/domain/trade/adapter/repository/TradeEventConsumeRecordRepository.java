package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;

import java.util.Optional;

public interface TradeEventConsumeRecordRepository {

    void save(TradeEventConsumeRecordEntity record);

    Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId);

    int updateStatusProcessing(TradeEventConsumeRecordEntity record);

    int updateStatusConsumed(TradeEventConsumeRecordEntity record);

    int updateStatusRetry(TradeEventConsumeRecordEntity record);

    int updateStatusDeadLetter(TradeEventConsumeRecordEntity record);
}















