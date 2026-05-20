package com.linrun.domain.knowledgeasset.model;

import java.time.LocalDateTime;

public class KnowledgeDocument {

    private String documentId;
    private String documentName;
    private String documentType;
    private String knowledgeVersion;
    private String sourceType;
    private String sourceName;
    private KnowledgeDocumentStatus documentStatus;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeDocument uploaded(String documentId,
                                             CreateKnowledgeDocumentCommand command,
                                             LocalDateTime now) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentId(documentId);
        document.setDocumentName(command.getDocumentName());
        document.setDocumentType(command.getDocumentType());
        document.setKnowledgeVersion(command.getKnowledgeVersion());
        document.setSourceType(command.getSourceType());
        document.setSourceName(command.getSourceName());
        document.setDocumentStatus(KnowledgeDocumentStatus.UPLOADED);
        document.setEnabled(false);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        return document;
    }

    public void markParsed() {
        if (KnowledgeDocumentStatus.PARSED.equals(documentStatus)) {
            return;
        }
        this.documentStatus = KnowledgeDocumentStatus.PARSED;
        this.enabled = false;
        this.updateTime = LocalDateTime.now();
    }

    public void enable() {
        if (KnowledgeDocumentStatus.ENABLED.equals(documentStatus)) {
            return;
        }
        this.documentStatus = KnowledgeDocumentStatus.ENABLED;
        this.enabled = true;
        this.updateTime = LocalDateTime.now();
    }

    public void disable() {
        if (KnowledgeDocumentStatus.DISABLED.equals(documentStatus)) {
            return;
        }
        this.documentStatus = KnowledgeDocumentStatus.DISABLED;
        this.enabled = false;
        this.updateTime = LocalDateTime.now();
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public KnowledgeDocumentStatus getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(KnowledgeDocumentStatus documentStatus) {
        this.documentStatus = documentStatus;
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
