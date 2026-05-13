package com.linrun.domain.prompt.model;

import java.time.LocalDateTime;

public class PromptTemplate {

    private String templateId;
    private PromptTemplateType templateType;
    private String templateVersion;
    private String content;
    private Boolean enabled;
    private LocalDateTime createTime;

    public static PromptTemplate enabled(String templateId,
                                         PromptTemplateType templateType,
                                         String templateVersion,
                                         String content) {
        PromptTemplate template = new PromptTemplate();
        template.setTemplateId(templateId);
        template.setTemplateType(templateType);
        template.setTemplateVersion(templateVersion);
        template.setContent(content);
        template.setEnabled(true);
        template.setCreateTime(LocalDateTime.now());
        return template;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public PromptTemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(PromptTemplateType templateType) {
        this.templateType = templateType;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(String templateVersion) {
        this.templateVersion = templateVersion;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
