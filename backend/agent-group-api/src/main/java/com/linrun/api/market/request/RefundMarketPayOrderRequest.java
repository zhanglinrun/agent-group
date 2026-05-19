package com.linrun.api.market.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundMarketPayOrderRequest implements Serializable {

    private String userId;
    private String outTradeNo;
    private String source;
    private String channel;
}
