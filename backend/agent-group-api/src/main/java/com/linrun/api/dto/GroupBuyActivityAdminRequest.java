package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运营端活动创建/编辑请求。
 */
@Data
public class GroupBuyActivityAdminRequest implements Serializable {

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
    /**
     * 总库存，仅创建活动时使用；编辑库存走专门的库存接口。
     */
    private Integer totalStock;
}
