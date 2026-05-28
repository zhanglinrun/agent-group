package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.TradeEventConsumeRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ITradeEventConsumeRecordDao {

    void insert(TradeEventConsumeRecordPO record);

    TradeEventConsumeRecordPO queryByEventId(@Param("eventId") String eventId);

    int updateStatusProcessing(TradeEventConsumeRecordPO record);

    int updateStatusConsumed(TradeEventConsumeRecordPO record);

    int updateStatusRetry(TradeEventConsumeRecordPO record);

    int updateStatusDeadLetter(TradeEventConsumeRecordPO record);
}
