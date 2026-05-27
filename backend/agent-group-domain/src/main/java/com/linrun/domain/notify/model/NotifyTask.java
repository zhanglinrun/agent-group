package com.linrun.domain.notify.model;

import java.time.LocalDateTime;

public class NotifyTask {

    public static final String CATEGORY_TRADE_SETTLEMENT = "trade_settlement";
    public static final String CATEGORY_TRADE_REFUND = "trade_refund";
    public static final String TYPE_HTTP = "HTTP";
    public static final String TYPE_MQ = "MQ";
    public static final int STATUS_INIT = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_RETRY = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_PROCESSING = 4;

    private Long id;
    private String activityId;
    private String teamId;
    private String notifyCategory;
    private String notifyType;
    private String notifyMq;
    private String notifyUrl;
    private Integer notifyCount;
    private Integer notifyStatus;
    private String parameterJson;
    private String uuid;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String lockKey() {
        return "notify_job_lock_key_" + uuid;
    }

    public void applyConfig(NotifyConfig config) {
        if (config == null) {
            return;
        }
        this.notifyType = config.getNotifyType();
        this.notifyMq = config.getNotifyMq();
        this.notifyUrl = config.getNotifyUrl();
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

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getNotifyCategory() {
        return notifyCategory;
    }

    public void setNotifyCategory(String notifyCategory) {
        this.notifyCategory = notifyCategory;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public String getNotifyMq() {
        return notifyMq;
    }

    public void setNotifyMq(String notifyMq) {
        this.notifyMq = notifyMq;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public Integer getNotifyCount() {
        return notifyCount;
    }

    public void setNotifyCount(Integer notifyCount) {
        this.notifyCount = notifyCount;
    }

    public Integer getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(Integer notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public String getParameterJson() {
        return parameterJson;
    }

    public void setParameterJson(String parameterJson) {
        this.parameterJson = parameterJson;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
