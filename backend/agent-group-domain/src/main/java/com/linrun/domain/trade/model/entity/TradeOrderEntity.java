package com.linrun.domain.trade.model.entity;

import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TradeOrderEntity {

    private Long id;
    private String orderId;
    private String idempotentKey;
    private String userId;
    private String goodsId;
    private String goodsName;
    private String activityId;
    private TradeBuyTypeEnumVO buyType;
    private BigDecimal originAmount;
    private BigDecimal payAmount;
    private TradeOrderStatusEnumVO orderStatus;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;

    public void waitPay() {
        if (TradeOrderStatusEnumVO.PAY_WAIT.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.CREATE.equals(orderStatus)) {
            throw new AppException("TRADE_0006", "当前订单状态不能创建支付单");
        }
        this.orderStatus = TradeOrderStatusEnumVO.PAY_WAIT;
    }

    public void markPaySuccess(LocalDateTime payTime) {
        if (TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)
                || TradeOrderStatusEnumVO.GROUP_SETTLED.equals(orderStatus)
                || TradeOrderStatusEnumVO.DEAL_DONE.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.PAY_WAIT.equals(orderStatus)) {
            throw new AppException("TRADE_0007", "当前订单状态不能改为支付成功");
        }
        this.payTime = payTime;
        this.orderStatus = TradeOrderStatusEnumVO.PAY_SUCCESS;
    }

    public void markGroupSettled() {
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(buyType)) {
            throw new AppException("TRADE_0008", "非拼团订单不能做拼团结算");
        }
        if (TradeOrderStatusEnumVO.GROUP_SETTLED.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)) {
            throw new AppException("TRADE_0009", "当前订单状态不能做拼团结算");
        }
        this.orderStatus = TradeOrderStatusEnumVO.GROUP_SETTLED;
    }

    public void markDealDone() {
        if (TradeOrderStatusEnumVO.DEAL_DONE.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)
                && !TradeOrderStatusEnumVO.GROUP_SETTLED.equals(orderStatus)) {
            throw new AppException("TRADE_0010", "当前订单状态不能完成交易");
        }
        this.orderStatus = TradeOrderStatusEnumVO.DEAL_DONE;
    }

    public void close(LocalDateTime closeTime) {
        if (TradeOrderStatusEnumVO.CLOSED.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.CREATE.equals(orderStatus) && !TradeOrderStatusEnumVO.PAY_WAIT.equals(orderStatus)) {
            throw new AppException("TRADE_0011", "当前订单状态不能关闭");
        }
        this.closeTime = closeTime;
        this.orderStatus = TradeOrderStatusEnumVO.CLOSED;
    }

    public void refund() {
        if (TradeOrderStatusEnumVO.REFUNDED.equals(orderStatus)) {
            return;
        }
        if (!TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)
                && !TradeOrderStatusEnumVO.GROUP_SETTLED.equals(orderStatus)
                && !TradeOrderStatusEnumVO.DEAL_DONE.equals(orderStatus)) {
            throw new AppException("TRADE_0015", "当前订单状态不能退款");
        }
        this.orderStatus = TradeOrderStatusEnumVO.REFUNDED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public TradeBuyTypeEnumVO getBuyType() {
        return buyType;
    }

    public void setBuyType(TradeBuyTypeEnumVO buyType) {
        this.buyType = buyType;
    }

    public BigDecimal getOriginAmount() {
        return originAmount;
    }

    public void setOriginAmount(BigDecimal originAmount) {
        this.originAmount = originAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public TradeOrderStatusEnumVO getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(TradeOrderStatusEnumVO orderStatus) {
        this.orderStatus = orderStatus;
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

    public LocalDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }
}















