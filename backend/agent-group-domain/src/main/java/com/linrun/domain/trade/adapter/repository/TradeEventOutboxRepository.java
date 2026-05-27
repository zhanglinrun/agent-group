package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;

import java.util.List;

public interface TradeEventOutboxRepository {

    void save(TradeEventOutboxEntity outbox);

    List<TradeEventOutboxEntity> queryPending(int limit);

    int updateStatusProcessing(TradeEventOutboxEntity outbox);

    int updateStatusSuccess(TradeEventOutboxEntity outbox);

    int updateStatusRetry(TradeEventOutboxEntity outbox);

    int updateStatusDeadLetter(TradeEventOutboxEntity outbox);

    static TradeEventOutboxRepository noop() {
        return new TradeEventOutboxRepository() {
            @Override
            public void save(TradeEventOutboxEntity outbox) {
            }

            @Override
            public List<TradeEventOutboxEntity> queryPending(int limit) {
                return List.of();
            }

            @Override
            public int updateStatusProcessing(TradeEventOutboxEntity outbox) {
                return 0;
            }

            @Override
            public int updateStatusSuccess(TradeEventOutboxEntity outbox) {
                return 0;
            }

            @Override
            public int updateStatusRetry(TradeEventOutboxEntity outbox) {
                return 0;
            }

            @Override
            public int updateStatusDeadLetter(TradeEventOutboxEntity outbox) {
                return 0;
            }
        };
    }
}
