package com.linrun.domain.trade.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;

public class TradeEventMessageEntity implements Serializable {

    private String flowId;
    private String orderId;
    private String bizType;
    private String bizId;
    private String eventType;
    private String routingKey;
    private String fromStatus;
    private String toStatus;
    private String remark;
    private LocalDateTime createTime;

    public static TradeEventMessageEntity fromFlow(TradeStatusFlowEntity flow) {
        TradeEventMessageEntity message = new TradeEventMessageEntity();
        message.setFlowId(flow.getFlowId());
        message.setOrderId(flow.getOrderId());
        message.setBizType(flow.getBizType());
        message.setBizId(flow.getBizId());
        message.setEventType(flow.getEventType());
        message.setRoutingKey(defaultRoutingKey(flow.getBizType(), flow.getEventType()));
        message.setFromStatus(flow.getFromStatus());
        message.setToStatus(flow.getToStatus());
        message.setRemark(flow.getRemark());
        message.setCreateTime(flow.getCreateTime());
        return message;
    }

    public static String defaultRoutingKey(String bizType, String eventType) {
        String routeBizType = bizType == null ? "unknown" : bizType.toLowerCase(Locale.ROOT);
        String routeEventType = eventType == null ? "unknown" : eventType.toLowerCase(Locale.ROOT);
        return "trade.event." + routeBizType + "." + routeEventType;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}















