package com.linrun.domain.trade.model.entity;

import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;

import java.math.BigDecimal;

public class CreateTradeOrderCommandEntity {

    private String userId;
    private String goodsId;
    private String goodsName;
    private String idempotentKey;
    private String activityId;
    private TradeBuyTypeEnumVO buyType;
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

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public TradeBuyTypeEnumVO getBuyType() {
        return buyType;
    }

    public void setBuyType(TradeBuyTypeEnumVO buyType) {
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















