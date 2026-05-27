package com.linrun.infrastructure.dao;

import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeEventOutboxDao {

    void insert(TradeEventOutboxEntity outbox);

    List<TradeEventOutboxEntity> queryPending(@Param("limit") int limit);

    int updateStatusProcessing(TradeEventOutboxEntity outbox);

    int updateStatusSuccess(TradeEventOutboxEntity outbox);

    int updateStatusRetry(TradeEventOutboxEntity outbox);

    int updateStatusDeadLetter(TradeEventOutboxEntity outbox);
}
