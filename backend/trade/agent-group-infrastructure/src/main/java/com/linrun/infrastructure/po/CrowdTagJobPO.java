package com.linrun.infrastructure.po;

import java.time.LocalDateTime;

public class CrowdTagJobPO {

    private Long id;
    private String tagId;
    private String batchId;
    private Integer tagType;
    private String tagRule;
    private LocalDateTime statStartTime;
    private LocalDateTime statEndTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Integer getTagType() {
        return tagType;
    }

    public void setTagType(Integer tagType) {
        this.tagType = tagType;
    }

    public String getTagRule() {
        return tagRule;
    }

    public void setTagRule(String tagRule) {
        this.tagRule = tagRule;
    }

    public LocalDateTime getStatStartTime() {
        return statStartTime;
    }

    public void setStatStartTime(LocalDateTime statStartTime) {
        this.statStartTime = statStartTime;
    }

    public LocalDateTime getStatEndTime() {
        return statEndTime;
    }

    public void setStatEndTime(LocalDateTime statEndTime) {
        this.statEndTime = statEndTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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















