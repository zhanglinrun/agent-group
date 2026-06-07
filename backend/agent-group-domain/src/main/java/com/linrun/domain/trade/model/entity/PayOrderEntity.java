package com.linrun.domain.trade.model.entity;

import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.types.exception.AppException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PayOrderEntity {

    private String payOrderId;
    private String orderId;
    private String payChannel;
    private BigDecimal payAmount;
    private PayStatusEnumVO payStatus;
    private String payUrl;
    private String outTradeNo;
    private LocalDateTime createTime;
    private LocalDateTime payTime;

    public static PayOrderEntity waitPay(String payOrderId, String orderId, BigDecimal payAmount, String payChannel,
                                   String payUrl, LocalDateTime now) {
        PayOrderEntity payOrder = new PayOrderEntity();
        payOrder.setPayOrderId(payOrderId);
        payOrder.setOrderId(orderId);
        payOrder.setPayAmount(payAmount);
        payOrder.setPayChannel(payChannel);
        payOrder.setPayUrl(payUrl);
        payOrder.setPayStatus(PayStatusEnumVO.WAIT_PAY);
        payOrder.setCreateTime(now);
        return payOrder;
    }

    public void markSuccess(String outTradeNo, LocalDateTime payTime) {
        if (PayStatusEnumVO.SUCCESS.equals(payStatus)) {
            return;
        }
        if (!PayStatusEnumVO.WAIT_PAY.equals(payStatus)) {
            throw new AppException("TRADE_0003", "当前支付单状态不能改为支付成功");
        }
        this.outTradeNo = outTradeNo;
        this.payTime = payTime;
        this.payStatus = PayStatusEnumVO.SUCCESS;
    }

    public void markGatewayCreated(String payChannel, String payUrl, String gatewayTradeNo) {
        if (!PayStatusEnumVO.WAIT_PAY.equals(payStatus)) {
            return;
        }
        if (hasText(payChannel)) {
            this.payChannel = payChannel;
        }
        if (hasText(payUrl)) {
            this.payUrl = payUrl;
        }
        if (hasText(gatewayTradeNo)) {
            this.outTradeNo = gatewayTradeNo;
        }
    }

    public void close() {
        if (PayStatusEnumVO.SUCCESS.equals(payStatus)) {
            throw new AppException("TRADE_0004", "支付成功的支付单不能关闭");
        }
        if (PayStatusEnumVO.REFUNDED.equals(payStatus)) {
            throw new AppException("TRADE_0005", "已退款的支付单不能关闭");
        }
        this.payStatus = PayStatusEnumVO.CLOSED;
    }

    public void refund() {
        if (PayStatusEnumVO.REFUNDED.equals(payStatus)) {
            return;
        }
        if (!PayStatusEnumVO.SUCCESS.equals(payStatus)) {
            throw new AppException("TRADE_0016", "当前支付单状态不能退款");
        }
        this.payStatus = PayStatusEnumVO.REFUNDED;
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

    public PayStatusEnumVO getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(PayStatusEnumVO payStatus) {
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
