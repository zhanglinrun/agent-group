package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundMarketPayOrderResponse implements Serializable {

    private String userId;
    private String orderId;
    private String teamId;
    private String code;
    private String info;
}
