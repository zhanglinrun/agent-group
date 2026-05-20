package com.linrun.api.order.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MockPayCallbackResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String orderStatus;
    private String payStatus;
    private String outTradeNo;
    private LocalDateTime payTime;
}
