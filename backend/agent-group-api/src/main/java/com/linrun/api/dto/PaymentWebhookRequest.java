package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PaymentWebhookRequest implements Serializable {

    private String payChannel;
    private String requestBody;
    private Map<String, String> headers;
    private String orderId;
    private String payOrderId;
    private String gatewayTradeNo;
    private BigDecimal payAmount;
    private String tradeStatus;
    private LocalDateTime payTime;
}















