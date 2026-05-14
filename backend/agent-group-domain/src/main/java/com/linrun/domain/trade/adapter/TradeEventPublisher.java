package com.linrun.domain.trade.adapter;

import com.linrun.domain.trade.model.TradeEventMessage;

public interface TradeEventPublisher {

    void publish(TradeEventMessage message);

    static TradeEventPublisher noop() {
        return message -> {
        };
    }
}
