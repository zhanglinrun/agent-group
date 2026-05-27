package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RefundGroupBuyOrderRequest implements Serializable {

    private String orderId;
    private String refundReason;
    private LocalDateTime refundTime;
}
