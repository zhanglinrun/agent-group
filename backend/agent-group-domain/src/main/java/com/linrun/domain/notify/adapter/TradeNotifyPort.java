package com.linrun.domain.notify.adapter;

import com.linrun.domain.notify.model.NotifyTask;

public interface TradeNotifyPort {

    void dispatch(NotifyTask task);

    static TradeNotifyPort noop() {
        return task -> {
        };
    }
}
