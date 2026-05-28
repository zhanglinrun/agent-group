package com.linrun.infrastructure.po;

import java.time.LocalDateTime;

public class KnowledgeFragmentPO {

    private String fragmentId;
    private String documentId;
    private String goodsId;
    private String documentType;
    private String knowledgeVersion;
    private String content;
    private Integer rankNo;
    private String parentFragmentId;
    private String brotherGroupId;
    private Integer brotherIndex;
    private Integer brotherTotal;
    private String chunkType;
    private Boolean embeddingEnabled;
    private String fragmentStatus;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getFragmentId() {
        return fragmentId;
    }

    public void setFragmentId(String fragmentId) {
        this.fragmentId = fragmentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public void setRankNo(Integer rankNo) {
        this.rankNo = rankNo;
    }

    public String getParentFragmentId() {
        return parentFragmentId;
    }

    public void setParentFragmentId(String parentFragmentId) {
        this.parentFragmentId = parentFragmentId;
    }

    public String getBrotherGroupId() {
        return brotherGroupId;
    }

    public void setBrotherGroupId(String brotherGroupId) {
        this.brotherGroupId = brotherGroupId;
    }

    public Integer getBrotherIndex() {
        return brotherIndex;
    }

    public void setBrotherIndex(Integer brotherIndex) {
        this.brotherIndex = brotherIndex;
    }

    public Integer getBrotherTotal() {
        return brotherTotal;
    }

    public void setBrotherTotal(Integer brotherTotal) {
        this.brotherTotal = brotherTotal;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public Boolean getEmbeddingEnabled() {
        return embeddingEnabled;
    }

    public void setEmbeddingEnabled(Boolean embeddingEnabled) {
        this.embeddingEnabled = embeddingEnabled;
    }

    public String getFragmentStatus() {
        return fragmentStatus;
    }

    public void setFragmentStatus(String fragmentStatus) {
        this.fragmentStatus = fragmentStatus;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
