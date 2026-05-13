package com.linrun.domain.guide.model;

import java.math.BigDecimal;

public class GuideProduct {

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getOriginPrice() {
        return originPrice;
    }

    public void setOriginPrice(BigDecimal originPrice) {
        this.originPrice = originPrice;
    }

    public BigDecimal getGroupPrice() {
        return groupPrice;
    }

    public void setGroupPrice(BigDecimal groupPrice) {
        this.groupPrice = groupPrice;
    }

    public String getSpecSummary() {
        return specSummary;
    }

    public void setSpecSummary(String specSummary) {
        this.specSummary = specSummary;
    }

    public String getAfterSalePolicy() {
        return afterSalePolicy;
    }

    public void setAfterSalePolicy(String afterSalePolicy) {
        this.afterSalePolicy = afterSalePolicy;
    }

    public String getRecommendReason() {
        return recommendReason;
    }

    public void setRecommendReason(String recommendReason) {
        this.recommendReason = recommendReason;
    }

    public String getNotSuitableFor() {
        return notSuitableFor;
    }

    public void setNotSuitableFor(String notSuitableFor) {
        this.notSuitableFor = notSuitableFor;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public Integer getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Integer remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }
}
