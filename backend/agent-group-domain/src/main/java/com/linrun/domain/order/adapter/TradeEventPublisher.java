package com.linrun.domain.order.adapter;

import com.linrun.domain.order.model.TradeEventMessage;

public interface TradeEventPublisher {

    void publish(TradeEventMessage message);

    static TradeEventPublisher noop() {
        return message -> {
        };
    }
}
