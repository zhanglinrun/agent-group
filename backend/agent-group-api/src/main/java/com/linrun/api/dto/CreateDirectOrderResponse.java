package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateDirectOrderResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String idempotentKey;
    private String decisionId;
    private String userId;
    private String goodsId;
    private String goodsName;
    private String buyType;
    private String orderStatus;
    private String payStatus;
    private BigDecimal originAmount;
    private BigDecimal payAmount;
    private String payUrl;
    private String payFormHtml;
    private String paymentType;
    private String payChannel;
    private String gatewayTradeNo;
    private LocalDateTime createTime;
}
