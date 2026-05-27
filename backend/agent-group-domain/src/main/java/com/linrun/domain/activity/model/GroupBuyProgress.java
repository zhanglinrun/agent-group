package com.linrun.domain.activity.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GroupBuyProgress {

    private Integer targetCount = 0;
    private Integer lockedCount = 0;
    private Integer completeCount = 0;
    private Integer remainingCount = 0;
    private BigDecimal progressRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private boolean success;

    public static GroupBuyProgress fromCounts(Integer targetCount, Integer lockedCount, Integer completeCount) {
        GroupBuyProgress progress = new GroupBuyProgress();
        int target = Math.max(value(targetCount), 0);
        int locked = Math.max(value(lockedCount), 0);
        int completed = Math.max(value(completeCount), 0);
        progress.setTargetCount(target);
        progress.setLockedCount(locked);
        progress.setCompleteCount(completed);
        progress.setRemainingCount(Math.max(target - locked, 0));
        progress.setSuccess(target > 0 && completed >= target);
        if (target > 0) {
            progress.setProgressRate(BigDecimal.valueOf(locked)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP)));
        }
        return progress;
    }

    public static GroupBuyProgress fromTeam(GroupBuyTeam team) {
        if (team == null) {
            return new GroupBuyProgress();
        }
        return fromCounts(team.getTargetCount(), team.getLockCount(), team.getCompleteCount());
    }

    public static GroupBuyProgress fromTeamDetail(GroupBuyTeamDetail detail) {
        if (detail == null) {
            return new GroupBuyProgress();
        }
        return fromCounts(detail.getTargetCount(), detail.getLockCount(), detail.getCompleteCount());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public Integer getLockedCount() {
        return lockedCount;
    }

    public void setLockedCount(Integer lockedCount) {
        this.lockedCount = lockedCount;
    }

    public Integer getCompleteCount() {
        return completeCount;
    }

    public void setCompleteCount(Integer completeCount) {
        this.completeCount = completeCount;
    }

    public Integer getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
    }

    public BigDecimal getProgressRate() {
        return progressRate;
    }

    public void setProgressRate(BigDecimal progressRate) {
        this.progressRate = progressRate == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : progressRate.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
