package com.linrun.domain.groupbuy.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class GroupBuyActivity {

    private Long id;
    private String activityId;
    private String goodsId;
    private BigDecimal groupPrice;
    private Integer teamSize;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean enabled;

    public GroupBuyActivityStatus resolveStatus(LocalDateTime now) {
        if (enabled == null || !enabled) {
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
