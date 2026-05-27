package com.linrun.domain.support.config.model;

import java.time.LocalDateTime;

public class DynamicConfig {

    private Long id;
    private String configKey;
    private String configValue;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DynamicConfig of(String configKey, String configValue, String remark) {
        DynamicConfig config = new DynamicConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setRemark(remark == null ? "" : remark);
        return config;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
