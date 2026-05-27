package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePaymentRequest implements Serializable {

    private String orderId;
    private String payChannel;
    private String notifyUrl;
    private String returnUrl;
}
