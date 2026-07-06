package com.linrun.infrastructure.po;

import java.time.LocalDateTime;

public class CrowdTagPO {

    private Long id;
    private String tagId;
    private String tagName;
    private String tagDesc;
    private Integer statistics;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String latestBatchId;
    private Integer latestJobStatus;

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

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTagDesc() {
        return tagDesc;
    }

    public void setTagDesc(String tagDesc) {
        this.tagDesc = tagDesc;
    }

    public Integer getStatistics() {
        return statistics;
    }

    public void setStatistics(Integer statistics) {
        this.statistics = statistics;
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

    public String getLatestBatchId() {
        return latestBatchId;
    }

    public void setLatestBatchId(String latestBatchId) {
        this.latestBatchId = latestBatchId;
    }

    public Integer getLatestJobStatus() {
        return latestJobStatus;
    }

    public void setLatestJobStatus(Integer latestJobStatus) {
        this.latestJobStatus = latestJobStatus;
    }
}















