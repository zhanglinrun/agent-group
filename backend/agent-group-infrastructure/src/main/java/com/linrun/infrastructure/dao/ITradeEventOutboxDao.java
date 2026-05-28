package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.TradeEventOutboxPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ITradeEventOutboxDao {

    void insert(TradeEventOutboxPO outbox);

    List<TradeEventOutboxPO> queryPending(@Param("limit") int limit);

    int updateStatusProcessing(TradeEventOutboxPO outbox);

    int updateStatusSuccess(TradeEventOutboxPO outbox);

    int updateStatusRetry(TradeEventOutboxPO outbox);

    int updateStatusDeadLetter(TradeEventOutboxPO outbox);
}
