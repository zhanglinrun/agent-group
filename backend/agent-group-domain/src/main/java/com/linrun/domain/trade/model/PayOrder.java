package com.linrun.domain.trade.model;

import com.linrun.types.exception.AppException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PayOrder {

    private String payOrderId;
    private String orderId;
    private String payChannel;
    private BigDecimal payAmount;
    private PayStatus payStatus;
    private String payUrl;
    private String outTradeNo;
    private LocalDateTime createTime;
    private LocalDateTime payTime;

    public static PayOrder waitPay(String payOrderId, String orderId, BigDecimal payAmount, String payChannel,
                                   String payUrl, LocalDateTime now) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderId(payOrderId);
        payOrder.setOrderId(orderId);
        payOrder.setPayAmount(payAmount);
        payOrder.setPayChannel(payChannel);
        payOrder.setPayUrl(payUrl);
        payOrder.setPayStatus(PayStatus.WAIT_PAY);
        payOrder.setCreateTime(now);
        return payOrder;
    }

    public void markSuccess(String outTradeNo, LocalDateTime payTime) {
        if (PayStatus.SUCCESS.equals(payStatus)) {
            return;
        }
        if (!PayStatus.WAIT_PAY.equals(payStatus)) {
            throw new AppException("TRADE_0003", "当前支付单状态不能改为支付成功");
        }
        this.outTradeNo = outTradeNo;
        this.payTime = payTime;
        this.payStatus = PayStatus.SUCCESS;
    }

    public void close() {
        if (PayStatus.SUCCESS.equals(payStatus)) {
            throw new AppException("TRADE_0004", "支付成功的支付单不能关闭");
        }
        if (PayStatus.REFUNDED.equals(payStatus)) {
            throw new AppException("TRADE_0005", "已退款的支付单不能关闭");
        }
        this.payStatus = PayStatus.CLOSED;
    }

    public void refund() {
        if (PayStatus.REFUNDED.equals(payStatus)) {
            return;
        }
        if (!PayStatus.SUCCESS.equals(payStatus)) {
            throw new AppException("TRADE_0016", "当前支付单状态不能退款");
        }
        this.payStatus = PayStatus.REFUNDED;
    }

    public String getPayOrderId() {
        return payOrderId;
    }

    public void setPayOrderId(String payOrderId) {
        this.payOrderId = payOrderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public PayStatus getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(PayStatus payStatus) {
        this.payStatus = payStatus;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getOutTradeNo() {
        return outTradeNo;
    }

    public void setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }
}
