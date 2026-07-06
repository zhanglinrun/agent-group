package com.linrun.domain.trade.adapter.port;

import com.linrun.domain.trade.model.notify.NotifyTask;

public interface TradeNotifyPort {

    void dispatch(NotifyTask task);

    static TradeNotifyPort noop() {
        return task -> {
        };
    }
}















