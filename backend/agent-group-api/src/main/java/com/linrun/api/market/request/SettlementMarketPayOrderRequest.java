package com.linrun.api.market.request;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SettlementMarketPayOrderRequest implements Serializable {

    private String source;
    private String channel;
    private String userId;
    private String outTradeNo;
    private LocalDateTime outTradeTime;
}
