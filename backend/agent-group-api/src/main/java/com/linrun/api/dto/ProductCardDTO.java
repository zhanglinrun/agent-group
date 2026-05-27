package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:27
 */
@Data
public class ProductCardDTO implements Serializable {

    private String decisionId;
    private java.time.LocalDateTime quoteExpireTime;
    private String goodsId;
    private String goodsName;
    private String imageUrl;
    private BigDecimal originPrice;
    private BigDecimal groupPrice;
    private String specSummary;
    private String afterSalePolicy;
    private String recommendReason;
    private String notSuitableFor;
    private String activityId;
    private Integer teamSize;
    private Integer remainingSeconds;
}
