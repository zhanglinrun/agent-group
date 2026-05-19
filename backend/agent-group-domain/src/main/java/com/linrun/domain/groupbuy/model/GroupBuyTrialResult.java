package com.linrun.domain.groupbuy.model;

import java.math.BigDecimal;

public class GroupBuyTrialResult {

    private String goodsId;
    private String goodsName;
    private String activityId;
    private String discountId;
    private String discountName;
    private String marketPlan;
    private String marketExpr;
    private String source;
    private String channel;
    private String tagId;
    private String tagScope;
    private BigDecimal originalPrice;
    private BigDecimal deductionPrice;
    private BigDecimal payPrice;
    private BigDecimal groupPrice;
    private Integer teamSize;
    private Integer takeLimitCount;
    private Integer validTime;
    private Integer remainingSeconds;
    private GroupBuyActivityStatus status;
    private boolean available;
    private boolean visible = true;
    private boolean enable = true;
    private String message;

    public static GroupBuyTrialResult missing(String goodsId) {
        GroupBuyTrialResult result = new GroupBuyTrialResult();
        result.setGoodsId(goodsId);
        result.setStatus(GroupBuyActivityStatus.MISSING);
        result.setAvailable(false);
        result.setRemainingSeconds(0);
        result.setMessage("当前商品没有配置拼团活动");
        return result;
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

    public String getDiscountId() {
        return discountId;
    }

    public void setDiscountId(String discountId) {
        this.discountId = discountId;
    }

    public String getDiscountName() {
        return discountName;
    }

    public void setDiscountName(String discountName) {
        this.discountName = discountName;
    }

    public String getMarketPlan() {
        return marketPlan;
    }

    public void setMarketPlan(String marketPlan) {
        this.marketPlan = marketPlan;
    }

    public String getMarketExpr() {
        return marketExpr;
    }

    public void setMarketExpr(String marketExpr) {
        this.marketExpr = marketExpr;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getTagScope() {
        return tagScope;
    }

    public void setTagScope(String tagScope) {
        this.tagScope = tagScope;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getDeductionPrice() {
        return deductionPrice;
    }

    public void setDeductionPrice(BigDecimal deductionPrice) {
        this.deductionPrice = deductionPrice;
    }

    public BigDecimal getPayPrice() {
        return payPrice;
    }

    public void setPayPrice(BigDecimal payPrice) {
        this.payPrice = payPrice;
    }

    public BigDecimal getGroupPrice() {
        return groupPrice;
    }

    public void setGroupPrice(BigDecimal groupPrice) {
        this.groupPrice = groupPrice;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public Integer getTakeLimitCount() {
        return takeLimitCount;
    }

    public void setTakeLimitCount(Integer takeLimitCount) {
        this.takeLimitCount = takeLimitCount;
    }

    public Integer getValidTime() {
        return validTime;
    }

    public void setValidTime(Integer validTime) {
        this.validTime = validTime;
    }

    public Integer getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Integer remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public GroupBuyActivityStatus getStatus() {
        return status;
    }

    public void setStatus(GroupBuyActivityStatus status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
