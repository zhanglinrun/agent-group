package com.linrun.domain.guide.model;

import java.time.LocalDateTime;

public class GuideConversationMessage {

    private GuideMessageRole role;
    private String content;
    private String imageUrl;
    private LocalDateTime createTime;

    public static GuideConversationMessage user(String content, String imageUrl) {
        GuideConversationMessage message = new GuideConversationMessage();
        message.setRole(GuideMessageRole.USER);
        message.setContent(content);
        message.setImageUrl(imageUrl);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    public static GuideConversationMessage assistant(String content) {
        GuideConversationMessage message = new GuideConversationMessage();
        message.setRole(GuideMessageRole.ASSISTANT);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    public GuideMessageRole getRole() {
        return role;
    }

    public void setRole(GuideMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
