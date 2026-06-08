package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TradeConsistencyCheckRequest implements Serializable {

    private String orderId;
    private String userId;
    private Integer pageSize;
}
