package com.linrun.domain.conversation.model;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class GuideDecisionSnapshot {

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

    public static GuideDecisionSnapshot capture(String sessionId,
                                                String requestId,
                                                String userId,
                                                String question,
                                                GuideDecisionResult decisionResult,
                                                List<GuideReference> references,
                                                AgentPlan agentPlan) {
        GuideProduct product = decisionResult == null ? null : decisionResult.getProduct();
        LocalDateTime now = LocalDateTime.now();
        GuideDecisionSnapshot snapshot = new GuideDecisionSnapshot();
        snapshot.setDecisionId(nextDecisionId(now));
        snapshot.setSessionId(sessionId);
        snapshot.setRequestId(requestId);
        snapshot.setUserId(userId);
        snapshot.setQuestion(question);
        if (product != null) {
            snapshot.setGoodsId(product.getGoodsId());
            snapshot.setGoodsName(product.getGoodsName());
            snapshot.setActivityId(product.getActivityId());
            snapshot.setOriginAmount(product.getOriginPrice());
            snapshot.setGroupAmount(product.getGroupPrice());
        }
        snapshot.setReferenceIds(joinReferenceIds(references));
        snapshot.setToolNames(joinToolNames(agentPlan));
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

    private static String joinReferenceIds(List<GuideReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        return references.stream()
                .map(GuideReference::getFragmentId)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String joinToolNames(AgentPlan agentPlan) {
        if (agentPlan == null || agentPlan.getTools() == null || agentPlan.getTools().isEmpty()) {
            return "";
        }
        return agentPlan.getTools().stream()
                .map(AgentToolCall::getName)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
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
