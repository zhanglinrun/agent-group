package com.linrun.infrastructure.po;

import java.time.LocalDateTime;

public class UserModelConfigPO {

    private String userId;
    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String textBaseUrl;
    private String textModel;
    private String imageBaseUrl;
    private String imageModel;
    private String encryptedApiKey;
    private String encryptedTextApiKey;
    private String encryptedImageApiKey;
    private String keyMasked;
    private String textKeyMasked;
    private String imageKeyMasked;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTextBaseUrl() {
        return textBaseUrl;
    }

    public void setTextBaseUrl(String textBaseUrl) {
        this.textBaseUrl = textBaseUrl;
    }

    public String getTextModel() {
        return textModel;
    }

    public void setTextModel(String textModel) {
        this.textModel = textModel;
    }

    public String getImageBaseUrl() {
        return imageBaseUrl;
    }

    public void setImageBaseUrl(String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }

    public String getEncryptedTextApiKey() {
        return encryptedTextApiKey;
    }

    public void setEncryptedTextApiKey(String encryptedTextApiKey) {
        this.encryptedTextApiKey = encryptedTextApiKey;
    }

    public String getEncryptedImageApiKey() {
        return encryptedImageApiKey;
    }

    public void setEncryptedImageApiKey(String encryptedImageApiKey) {
        this.encryptedImageApiKey = encryptedImageApiKey;
    }

    public String getKeyMasked() {
        return keyMasked;
    }

    public void setKeyMasked(String keyMasked) {
        this.keyMasked = keyMasked;
    }

    public String getTextKeyMasked() {
        return textKeyMasked;
    }

    public void setTextKeyMasked(String textKeyMasked) {
        this.textKeyMasked = textKeyMasked;
    }

    public String getImageKeyMasked() {
        return imageKeyMasked;
    }

    public void setImageKeyMasked(String imageKeyMasked) {
        this.imageKeyMasked = imageKeyMasked;
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
