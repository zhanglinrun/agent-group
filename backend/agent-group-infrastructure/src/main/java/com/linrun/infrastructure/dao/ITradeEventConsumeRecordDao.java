package com.linrun.infrastructure.dao;

import com.linrun.domain.order.model.entity.TradeEventConsumeRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ITradeEventConsumeRecordDao {

    void insert(TradeEventConsumeRecordEntity record);

    TradeEventConsumeRecordEntity queryByEventId(@Param("eventId") String eventId);

    int updateStatusProcessing(TradeEventConsumeRecordEntity record);

    int updateStatusConsumed(TradeEventConsumeRecordEntity record);

    int updateStatusRetry(TradeEventConsumeRecordEntity record);

    int updateStatusDeadLetter(TradeEventConsumeRecordEntity record);
}
