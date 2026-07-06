package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运营端活动详情/列表项响应，含库存摘要。
 */
@Data
public class GroupBuyActivityAdminResponse implements Serializable {

    private String activityId;
    private String activityName;
    private String goodsId;
    private BigDecimal groupPrice;
    private Integer teamSize;
    private String discountId;
    private Integer groupType;
    private Integer takeLimitCount;
    private Integer target;
    private Integer validTime;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String tagId;
    private String tagScope;
    private Boolean enabled;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer paidStock;
}
