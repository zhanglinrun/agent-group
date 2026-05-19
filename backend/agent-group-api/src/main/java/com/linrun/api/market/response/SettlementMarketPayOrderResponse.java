package com.linrun.api.market.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class SettlementMarketPayOrderResponse implements Serializable {

    private String userId;
    private String teamId;
    private String activityId;
    private String outTradeNo;
}
