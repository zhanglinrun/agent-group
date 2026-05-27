package com.linrun.domain.agent.knowledge.model;

import java.time.LocalDateTime;

public class KnowledgeFragment {

    private String fragmentId;
    private String documentId;
    private String goodsId;
    private String documentType;
    private String knowledgeVersion;
    private String content;
    private Integer rankNo;
    private KnowledgeFragmentStatus fragmentStatus;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeFragment enabled(String fragmentId,
                                            KnowledgeDocument document,
                                            CreateKnowledgeFragmentCommand command,
                                            LocalDateTime now) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setDocumentId(document.getDocumentId());
        fragment.setGoodsId(command.getGoodsId());
        fragment.setDocumentType(document.getDocumentType());
        fragment.setKnowledgeVersion(document.getKnowledgeVersion());
        fragment.setContent(command.getContent());
        fragment.setRankNo(command.getRankNo());
        fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
        fragment.setEnabled(true);
        fragment.setCreateTime(now);
        fragment.setUpdateTime(now);
        return fragment;
    }

    public void disable() {
        if (KnowledgeFragmentStatus.DISABLED.equals(fragmentStatus)) {
            return;
        }
        this.fragmentStatus = KnowledgeFragmentStatus.DISABLED;
        this.enabled = false;
        this.updateTime = LocalDateTime.now();
    }

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

    public KnowledgeFragmentStatus getFragmentStatus() {
        return fragmentStatus;
    }

    public void setFragmentStatus(KnowledgeFragmentStatus fragmentStatus) {
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
