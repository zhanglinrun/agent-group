package com.linrun.domain.groupbuy.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class GroupBuyActivity {

    private Long id;
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

    public GroupBuyActivityStatus resolveStatus(LocalDateTime now) {
        if (enabled == null || !enabled) {
            return GroupBuyActivityStatus.DISABLED;
        }
        if (status != null && status != 1) {
            return GroupBuyActivityStatus.DISABLED;
        }
        if (startTime != null && now.isBefore(startTime)) {
            return GroupBuyActivityStatus.NOT_STARTED;
        }
        if (endTime != null && !now.isBefore(endTime)) {
            return GroupBuyActivityStatus.ENDED;
        }
        return GroupBuyActivityStatus.ACTIVE;
    }

    public int remainingSeconds(LocalDateTime now) {
        if (endTime == null || !endTime.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, endTime).toSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    public int resolveTeamSize() {
        if (target != null && target > 0 && (teamSize == null || target > 1)) {
            return target;
        }
        if (teamSize != null && teamSize > 0) {
            return teamSize;
        }
        return 1;
    }

    public LocalDateTime resolveTeamValidEndTime(LocalDateTime now) {
        LocalDateTime validEndTime = endTime;
        if (validTime != null && validTime > 0) {
            LocalDateTime byValidTime = now.plusMinutes(validTime);
            if (validEndTime == null || byValidTime.isBefore(validEndTime)) {
                validEndTime = byValidTime;
            }
        }
        return validEndTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
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

    public String getDiscountId() {
        return discountId;
    }

    public void setDiscountId(String discountId) {
        this.discountId = discountId;
    }

    public Integer getGroupType() {
        return groupType;
    }

    public void setGroupType(Integer groupType) {
        this.groupType = groupType;
    }

    public Integer getTakeLimitCount() {
        return takeLimitCount;
    }

    public void setTakeLimitCount(Integer takeLimitCount) {
        this.takeLimitCount = takeLimitCount;
    }

    public Integer getTarget() {
        return target;
    }

    public void setTarget(Integer target) {
        this.target = target;
    }

    public Integer getValidTime() {
        return validTime;
    }

    public void setValidTime(Integer validTime) {
        this.validTime = validTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
