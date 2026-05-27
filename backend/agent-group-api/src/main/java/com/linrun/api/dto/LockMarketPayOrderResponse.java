package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LockMarketPayOrderResponse implements Serializable {

    private String orderId;
    private BigDecimal originalPrice;
    private BigDecimal deductionPrice;
    private BigDecimal payPrice;
    private Integer tradeOrderStatus;
    private String teamId;
}
