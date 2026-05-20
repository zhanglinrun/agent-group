package com.linrun.api.order.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateDirectOrderResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String userId;
    private String goodsId;
    private String goodsName;
    private String buyType;
    private String orderStatus;
    private String payStatus;
    private BigDecimal originAmount;
    private BigDecimal payAmount;
    private String payUrl;
    private LocalDateTime createTime;
}
