package com.linrun.domain.order.model;

import java.math.BigDecimal;

public class CreateTradeOrderCommand {

    private String userId;
    private String goodsId;
    private String goodsName;
    private String activityId;
    private TradeBuyType buyType;
    private BigDecimal originAmount;
    private BigDecimal payAmount;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public TradeBuyType getBuyType() {
        return buyType;
    }

    public void setBuyType(TradeBuyType buyType) {
        this.buyType = buyType;
    }

    public BigDecimal getOriginAmount() {
        return originAmount;
    }

    public void setOriginAmount(BigDecimal originAmount) {
        this.originAmount = originAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }
}
