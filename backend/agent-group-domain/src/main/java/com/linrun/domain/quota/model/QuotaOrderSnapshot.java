package com.linrun.domain.quota.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class QuotaOrderSnapshot {

    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int DEFAULT_QUOTE_TTL_MINUTES = 15;

    private String decisionId;
    private String sessionId;
    private String requestId;
    private String userId;
    private String question;
    private String goodsId;
    private String goodsName;
    private String activityId;
    private BigDecimal originAmount;
    private BigDecimal groupAmount;
    private String referenceIds;
    private String toolNames;
    private LocalDateTime quoteExpireTime;
    private LocalDateTime createTime;

    public static QuotaOrderSnapshot captureQuote(String sessionId,
                                                     String requestId,
                                                     String userId,
                                                     String question,
                                                     QuotaProduct product) {
        LocalDateTime now = LocalDateTime.now();
        QuotaOrderSnapshot snapshot = new QuotaOrderSnapshot();
        snapshot.setDecisionId(nextDecisionId(now));
        snapshot.setSessionId(sessionId);
        snapshot.setRequestId(requestId);
        snapshot.setUserId(userId);
        snapshot.setQuestion(question);
        snapshot.setGoodsId("");
        snapshot.setGoodsName("");
        snapshot.setActivityId("");
        snapshot.setOriginAmount(BigDecimal.ZERO);
        snapshot.setGroupAmount(BigDecimal.ZERO);
        if (product != null) {
            snapshot.setGoodsId(safeText(product.getGoodsId()));
            snapshot.setGoodsName(safeText(product.getGoodsName()));
            snapshot.setActivityId(safeText(product.getActivityId()));
            snapshot.setOriginAmount(safeAmount(product.getOriginPrice()));
            snapshot.setGroupAmount(safeAmount(product.getGroupPrice()));
        }
        snapshot.setReferenceIds("");
        snapshot.setToolNames("");
        snapshot.setQuoteExpireTime(now.plusMinutes(DEFAULT_QUOTE_TTL_MINUTES));
        snapshot.setCreateTime(now);
        return snapshot;
    }

    public boolean isExpired(LocalDateTime now) {
        return quoteExpireTime != null && !quoteExpireTime.isAfter(now == null ? LocalDateTime.now() : now);
    }

    private static String nextDecisionId(LocalDateTime now) {
        return "D" + now.format(ID_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public BigDecimal getOriginAmount() {
        return originAmount;
    }

    public void setOriginAmount(BigDecimal originAmount) {
        this.originAmount = originAmount;
    }

    public BigDecimal getGroupAmount() {
        return groupAmount;
    }

    public void setGroupAmount(BigDecimal groupAmount) {
        this.groupAmount = groupAmount;
    }

    public String getReferenceIds() {
        return referenceIds;
    }

    public void setReferenceIds(String referenceIds) {
        this.referenceIds = referenceIds;
    }

    public String getToolNames() {
        return toolNames;
    }

    public void setToolNames(String toolNames) {
        this.toolNames = toolNames;
    }

    public LocalDateTime getQuoteExpireTime() {
        return quoteExpireTime;
    }

    public void setQuoteExpireTime(LocalDateTime quoteExpireTime) {
        this.quoteExpireTime = quoteExpireTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
















