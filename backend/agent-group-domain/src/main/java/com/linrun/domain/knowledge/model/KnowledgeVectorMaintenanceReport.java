package com.linrun.domain.knowledge.model;

import java.math.BigDecimal;
import java.util.List;

public class KnowledgeVectorMaintenanceReport {

    private String action;
    private String knowledgeVersion;
    private Integer fragmentCount;
    private Integer successCount;
    private Integer expectedCount;
    private Integer matchedCount;
    private BigDecimal recallHitRate;
    private String snapshotId;
    private List<String> hitFragmentIds;
    private String message;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public Integer getFragmentCount() {
        return fragmentCount;
    }

    public void setFragmentCount(Integer fragmentCount) {
        this.fragmentCount = fragmentCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(Integer expectedCount) {
        this.expectedCount = expectedCount;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public BigDecimal getRecallHitRate() {
        return recallHitRate;
    }

    public void setRecallHitRate(BigDecimal recallHitRate) {
        this.recallHitRate = recallHitRate;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public List<String> getHitFragmentIds() {
        return hitFragmentIds;
    }

    public void setHitFragmentIds(List<String> hitFragmentIds) {
        this.hitFragmentIds = hitFragmentIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
