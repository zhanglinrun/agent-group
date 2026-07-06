package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;

public interface TradeEventPublisher {

    void publish(TradeEventMessageEntity message);

    static TradeEventPublisher noop() {
        return message -> {
        };
    }
}















