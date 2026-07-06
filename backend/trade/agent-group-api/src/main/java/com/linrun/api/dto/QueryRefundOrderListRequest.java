package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class QueryRefundOrderListRequest implements Serializable {

    private String userId;
    private String refundStatus;
    private Integer pageSize;
}















