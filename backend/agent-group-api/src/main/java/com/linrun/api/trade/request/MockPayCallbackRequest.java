package com.linrun.api.trade.request;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MockPayCallbackRequest implements Serializable {

    private String orderId;
    private String outTradeNo;
    private LocalDateTime payTime;
}
