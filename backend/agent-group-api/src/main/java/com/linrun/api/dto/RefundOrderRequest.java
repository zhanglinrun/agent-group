package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundOrderRequest implements Serializable {

    private String userId;
    private String orderId;
    private String refundReason;
}
