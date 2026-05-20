package com.linrun.domain.order.model;

public enum TradeOrderStatus {

    CREATE,
    PAY_WAIT,
    PAY_SUCCESS,
    GROUP_SETTLED,
    DEAL_DONE,
    CLOSED,
    WAIT_REFUND,
    REFUNDED
}
