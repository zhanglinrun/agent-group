package com.linrun.domain.order.adapter;

import com.linrun.domain.order.model.entity.TradeEventMessageEntity;

public interface TradeEventPublisher {

    void publish(TradeEventMessageEntity message);

    static TradeEventPublisher noop() {
        return message -> {
        };
    }
}
