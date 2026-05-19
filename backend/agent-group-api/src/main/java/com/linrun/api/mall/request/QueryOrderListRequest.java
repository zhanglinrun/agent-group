package com.linrun.api.mall.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class QueryOrderListRequest implements Serializable {

    private String userId;
    private Long lastId;
    private Integer pageSize;
}
