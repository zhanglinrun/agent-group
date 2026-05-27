package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SettlementMarketPayOrderRequest implements Serializable {

    private String source;
    private String channel;
    private String userId;
    private String outTradeNo;
    private String hitlApprovalId;
    private LocalDateTime outTradeTime;
}
