package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class MallProductDTO implements Serializable {

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
    private boolean groupBuyAvailable;
    private String marketMessage;
}
