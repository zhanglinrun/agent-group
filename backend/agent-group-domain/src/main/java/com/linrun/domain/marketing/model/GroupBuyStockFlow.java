package com.linrun.domain.marketing.model;

import java.time.LocalDateTime;

public class GroupBuyStockFlow {

    private Long id;
    private String flowId;
    private String activityId;
    private String goodsId;
    private String teamId;
    private String orderId;
    private GroupBuyStockFlowType flowType;
    private Integer quantity;
    private Integer beforeAvailableStock;
    private Integer afterAvailableStock;
    private String remark;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public GroupBuyStockFlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(GroupBuyStockFlowType flowType) {
        this.flowType = flowType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getBeforeAvailableStock() {
        return beforeAvailableStock;
    }

    public void setBeforeAvailableStock(Integer beforeAvailableStock) {
        this.beforeAvailableStock = beforeAvailableStock;
    }

    public Integer getAfterAvailableStock() {
        return afterAvailableStock;
    }

    public void setAfterAvailableStock(Integer afterAvailableStock) {
        this.afterAvailableStock = afterAvailableStock;
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
