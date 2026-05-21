package com.linrun.domain.order.model.entity;

import java.time.LocalDateTime;

public class TradeEventConsumeRecordEntity {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_CONSUMED = 1;
    public static final int STATUS_RETRY = 2;
    public static final int STATUS_DEAD_LETTER = 3;
    public static final int STATUS_PROCESSING = 4;

    private Long id;
    private String eventId;
    private String orderId;
    private String bizType;
    private String bizId;
    private String eventType;
    private String routingKey;
    private Integer consumeCount;
    private Integer consumeStatus;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static TradeEventConsumeRecordEntity fromMessage(TradeEventMessageEntity message) {
        TradeEventConsumeRecordEntity record = new TradeEventConsumeRecordEntity();
        record.setEventId(message.getFlowId());
        record.setOrderId(message.getOrderId());
        record.setBizType(message.getBizType());
        record.setBizId(message.getBizId());
        record.setEventType(message.getEventType());
        record.setRoutingKey(message.getRoutingKey() == null
                ? TradeEventMessageEntity.defaultRoutingKey(message.getBizType(), message.getEventType())
                : message.getRoutingKey());
        record.setConsumeCount(0);
        record.setConsumeStatus(STATUS_INIT);
        record.setCreateTime(message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime());
        return record;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public Integer getConsumeCount() {
        return consumeCount;
    }

    public void setConsumeCount(Integer consumeCount) {
        this.consumeCount = consumeCount;
    }

    public Integer getConsumeStatus() {
        return consumeStatus;
    }

    public void setConsumeStatus(Integer consumeStatus) {
        this.consumeStatus = consumeStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
