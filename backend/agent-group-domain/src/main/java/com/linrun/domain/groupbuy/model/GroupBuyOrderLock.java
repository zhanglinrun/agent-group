package com.linrun.domain.groupbuy.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GroupBuyOrderLock {

    private Long id;
    private String lockId;
    private String idempotentKey;
    private String userId;
    private String teamId;
    private String orderId;
    private String activityId;
    private String goodsId;
    private BigDecimal lockAmount;
    private GroupBuyLockStatus lockStatus;
    private LocalDateTime lockTime;

    public static GroupBuyOrderLock locked(String lockId,
                                           String idempotentKey,
                                           String userId,
                                           String teamId,
                                           GroupBuyActivity activity,
                                           LocalDateTime now) {
        GroupBuyOrderLock orderLock = new GroupBuyOrderLock();
        orderLock.setLockId(lockId);
        orderLock.setIdempotentKey(idempotentKey);
        orderLock.setUserId(userId);
        orderLock.setTeamId(teamId);
        orderLock.setActivityId(activity.getActivityId());
        orderLock.setGoodsId(activity.getGoodsId());
        orderLock.setLockAmount(activity.getGroupPrice());
        orderLock.setLockStatus(GroupBuyLockStatus.LOCKED);
        orderLock.setLockTime(now);
        return orderLock;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLockId() {
        return lockId;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public BigDecimal getLockAmount() {
        return lockAmount;
    }

    public void setLockAmount(BigDecimal lockAmount) {
        this.lockAmount = lockAmount;
    }

    public GroupBuyLockStatus getLockStatus() {
        return lockStatus;
    }

    public void setLockStatus(GroupBuyLockStatus lockStatus) {
        this.lockStatus = lockStatus;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }
}
