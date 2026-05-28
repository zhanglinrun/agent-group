package com.linrun.domain.agent.conversation.model;

public class GuideReference {

    private String fragmentId;
    private String documentId;
    private String goodsId;
    private String documentType;
    private String knowledgeVersion;
    private String content;
    private Integer rank;
    private String parentFragmentId;
    private String brotherGroupId;
    private Integer brotherIndex;
    private Integer brotherTotal;
    private String chunkType;

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

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
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
}
