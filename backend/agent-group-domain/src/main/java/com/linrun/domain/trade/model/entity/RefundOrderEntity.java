package com.linrun.domain.trade.model.entity;

import com.linrun.domain.trade.model.valobj.RefundStatusEnumVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundOrderEntity {

    private String refundId;
    private String orderId;
    private String payOrderId;
    private String userId;
    private BigDecimal refundAmount;
    private RefundStatusEnumVO refundStatus;
    private String refundReason;
    private LocalDateTime createTime;
    private LocalDateTime refundTime;

    public static RefundOrderEntity success(String refundId,
                                      TradeOrderEntity tradeOrder,
                                      PayOrderEntity payOrder,
                                      String refundReason,
                                      LocalDateTime refundTime) {
        RefundOrderEntity refundOrder = new RefundOrderEntity();
        refundOrder.setRefundId(refundId);
        refundOrder.setOrderId(tradeOrder.getOrderId());
        refundOrder.setPayOrderId(payOrder.getPayOrderId());
        refundOrder.setUserId(tradeOrder.getUserId());
        refundOrder.setRefundAmount(payOrder.getPayAmount());
        refundOrder.setRefundStatus(RefundStatusEnumVO.SUCCESS);
        refundOrder.setRefundReason(refundReason);
        refundOrder.setCreateTime(refundTime);
        refundOrder.setRefundTime(refundTime);
        return refundOrder;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPayOrderId() {
        return payOrderId;
    }

    public void setPayOrderId(String payOrderId) {
        this.payOrderId = payOrderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public RefundStatusEnumVO getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(RefundStatusEnumVO refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(LocalDateTime refundTime) {
        this.refundTime = refundTime;
    }
}
