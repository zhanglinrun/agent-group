package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 运营端活动库存调整请求。
 */
@Data
public class GroupBuyActivityStockRequest implements Serializable {

    private Integer totalStock;
}
