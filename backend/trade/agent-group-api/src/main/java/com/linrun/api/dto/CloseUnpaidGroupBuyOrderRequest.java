package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CloseUnpaidGroupBuyOrderRequest implements Serializable {

    private String orderId;
    private LocalDateTime closeTime;
}















