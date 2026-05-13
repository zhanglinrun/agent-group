package com.linrun.domain.groupbuy.model;

import java.math.BigDecimal;

public class GroupBuyTrialResult {

    private String goodsId;
    private String activityId;
    private BigDecimal groupPrice;
    private Integer teamSize;
    private Integer remainingSeconds;
    private GroupBuyActivityStatus status;
    private boolean available;
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

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
