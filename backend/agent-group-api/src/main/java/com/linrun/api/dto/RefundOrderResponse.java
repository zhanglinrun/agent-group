package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundOrderResponse implements Serializable {

    private boolean success;
    private String orderId;
    private String message;
}
