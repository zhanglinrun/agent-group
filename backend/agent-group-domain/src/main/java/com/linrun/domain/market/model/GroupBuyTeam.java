package com.linrun.domain.market.model;

import com.linrun.types.exception.AppException;

import java.time.LocalDateTime;

public class GroupBuyTeam {

    private Long id;
    private String teamId;
    private String activityId;
    private String goodsId;
    private Integer targetCount;
    private Integer completeCount;
    private Integer lockCount;
    private GroupBuyTeamStatus teamStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime createTime;

    public static GroupBuyTeam create(String teamId, GroupBuyActivity activity, LocalDateTime now) {
        GroupBuyTeam team = new GroupBuyTeam();
        team.setTeamId(teamId);
        team.setActivityId(activity.getActivityId());
        team.setGoodsId(activity.getGoodsId());
        team.setTargetCount(activity.resolveTeamSize());
        team.setCompleteCount(0);
        team.setLockCount(1);
        team.setTeamStatus(GroupBuyTeamStatus.PROCESSING);
        team.setValidStartTime(now);
        team.setValidEndTime(activity.resolveTeamValidEndTime(now));
        team.setCreateTime(now);
        return team;
    }

    public void assertCanJoin(String activityId, String goodsId, LocalDateTime now) {
        if (!this.activityId.equals(activityId) || !this.goodsId.equals(goodsId)) {
            throw new AppException("GROUP_0004", "拼团队伍和活动额度包不匹配");
        }
        if (!GroupBuyTeamStatus.PROCESSING.equals(teamStatus)) {
            throw new AppException("GROUP_0005", "拼团队伍不可加入");
        }
        if (validEndTime != null && !now.isBefore(validEndTime)) {
            throw new AppException("GROUP_0006", "拼团队伍已过本");
        }
        if (lockCount != null && targetCount != null && lockCount >= targetCount) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
    }

    public int remainingCount() {
        if (targetCount == null) {
            return 0;
        }
        int locked = lockCount == null ? 0 : lockCount;
        return Math.max(targetCount - locked, 0);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
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

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public Integer getCompleteCount() {
        return completeCount;
    }

    public void setCompleteCount(Integer completeCount) {
        this.completeCount = completeCount;
    }

    public Integer getLockCount() {
        return lockCount;
    }

    public void setLockCount(Integer lockCount) {
        this.lockCount = lockCount;
    }

    public GroupBuyTeamStatus getTeamStatus() {
        return teamStatus;
    }

    public void setTeamStatus(GroupBuyTeamStatus teamStatus) {
        this.teamStatus = teamStatus;
    }

    public LocalDateTime getValidStartTime() {
        return validStartTime;
    }

    public void setValidStartTime(LocalDateTime validStartTime) {
        this.validStartTime = validStartTime;
    }

    public LocalDateTime getValidEndTime() {
        return validEndTime;
    }

    public void setValidEndTime(LocalDateTime validEndTime) {
        this.validEndTime = validEndTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}















