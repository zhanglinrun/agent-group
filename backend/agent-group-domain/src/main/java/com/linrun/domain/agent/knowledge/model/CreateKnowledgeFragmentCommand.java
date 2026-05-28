package com.linrun.domain.agent.knowledge.model;

public class CreateKnowledgeFragmentCommand {

    private String goodsId;
    private String content;
    private Integer rankNo;
    private String parentKey;
    private String parentFragmentId;
    private String brotherGroupId;
    private Integer brotherIndex;
    private Integer brotherTotal;
    private String chunkType;
    private Boolean embeddingEnabled;

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
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

    public String getParentKey() {
        return parentKey;
    }

    public void setParentKey(String parentKey) {
        this.parentKey = parentKey;
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
}
