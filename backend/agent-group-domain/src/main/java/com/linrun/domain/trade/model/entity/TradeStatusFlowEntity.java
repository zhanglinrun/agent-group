package com.linrun.domain.trade.model.entity;

import java.time.LocalDateTime;

public class TradeStatusFlowEntity {

    private String flowId;
    private String orderId;
    private String bizType;
    private String bizId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String remark;
    private LocalDateTime createTime;

    public static TradeStatusFlowEntity record(String flowId,
                                         String orderId,
                                         String bizType,
                                         String bizId,
                                         String eventType,
                                         String fromStatus,
                                         String toStatus,
                                         String remark,
                                         LocalDateTime createTime) {
        TradeStatusFlowEntity flow = new TradeStatusFlowEntity();
        flow.setFlowId(flowId);
        flow.setOrderId(orderId);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setEventType(eventType);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setRemark(remark);
        flow.setCreateTime(createTime);
        return flow;
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















