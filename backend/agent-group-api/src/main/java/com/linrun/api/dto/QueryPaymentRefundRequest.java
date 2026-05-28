package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class QueryPaymentRefundRequest implements Serializable {

    private String orderId;
    private String payChannel;
    private String payOrderId;
    private String gatewayTradeNo;
    private String refundId;
}
