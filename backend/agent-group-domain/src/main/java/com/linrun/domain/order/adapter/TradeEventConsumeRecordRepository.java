package com.linrun.domain.order.adapter;

import com.linrun.domain.order.model.entity.TradeEventConsumeRecordEntity;

import java.util.Optional;

public interface TradeEventConsumeRecordRepository {

    void save(TradeEventConsumeRecordEntity record);

    Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId);

    int updateStatusProcessing(TradeEventConsumeRecordEntity record);

    int updateStatusConsumed(TradeEventConsumeRecordEntity record);

    int updateStatusRetry(TradeEventConsumeRecordEntity record);

    int updateStatusDeadLetter(TradeEventConsumeRecordEntity record);
}
