package com.linrun.domain.order.model.entity;

import java.time.LocalDateTime;

public class TradeEventOutboxEntity {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_SUCCESS = 1;
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
    private String fromStatus;
    private String toStatus;
    private String remark;
    private Integer sendCount;
    private Integer sendStatus;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static TradeEventOutboxEntity fromMessage(TradeEventMessageEntity message) {
        TradeEventOutboxEntity outbox = new TradeEventOutboxEntity();
        outbox.setEventId(message.getFlowId());
        outbox.setOrderId(message.getOrderId());
        outbox.setBizType(message.getBizType());
        outbox.setBizId(message.getBizId());
        outbox.setEventType(message.getEventType());
        outbox.setRoutingKey(message.getRoutingKey() == null
                ? TradeEventMessageEntity.defaultRoutingKey(message.getBizType(), message.getEventType())
                : message.getRoutingKey());
        outbox.setFromStatus(message.getFromStatus());
        outbox.setToStatus(message.getToStatus());
        outbox.setRemark(message.getRemark());
        outbox.setSendCount(0);
        outbox.setSendStatus(STATUS_INIT);
        outbox.setCreateTime(message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime());
        return outbox;
    }

    public TradeEventMessageEntity toMessage() {
        TradeEventMessageEntity message = new TradeEventMessageEntity();
        message.setFlowId(eventId);
        message.setOrderId(orderId);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setEventType(eventType);
        message.setRoutingKey(routingKey);
        message.setFromStatus(fromStatus);
        message.setToStatus(toStatus);
        message.setRemark(remark);
        message.setCreateTime(createTime);
        return message;
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

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSendCount() {
        return sendCount;
    }

    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    public Integer getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(Integer sendStatus) {
        this.sendStatus = sendStatus;
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
