package com.linrun.domain.groupbuy.tag.model;

import java.util.List;

public class CrowdTagJobResult {

    private String tagId;
    private String batchId;
    private Integer tagType;
    private String tagRule;
    private int matchedCount;
    private List<String> userIds;
    private String message;

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

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}















