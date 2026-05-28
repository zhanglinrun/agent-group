package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class QueryOrderListRequest implements Serializable {

    private String userId;
    private Long lastId;
    private Integer pageSize;
    private Integer marketType;
    private String orderStatus;
    private String keyword;
}
