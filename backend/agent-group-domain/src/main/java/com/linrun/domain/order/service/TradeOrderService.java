package com.linrun.domain.order.service;

import com.linrun.domain.order.model.entity.CreateTradeOrderCommandEntity;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.order.model.aggregate.TradePayOrderAggregate;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class TradeOrderService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public TradeOrderEntity createOrder(CreateTradeOrderCommandEntity command) {
        validateCreateCommand(command);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setOrderId(nextNo("O"));
        order.setUserId(command.getUserId());
        order.setGoodsId(command.getGoodsId());
        order.setGoodsName(command.getGoodsName());
        order.setActivityId(command.getActivityId());
        order.setBuyType(command.getBuyType());
        order.setOriginAmount(command.getOriginAmount());
        order.setPayAmount(command.getPayAmount());
        order.setOrderStatus(TradeOrderStatusEnumVO.CREATE);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    public TradePayOrderAggregate createPayOrder(TradeOrderEntity order, String payChannel) {
        if (!StringUtils.hasText(payChannel)) {
            throw new AppException("0001", "支付渠道不能为空");
        }
        order.waitPay();

        PayOrderEntity payOrder = PayOrderEntity.waitPay(
                nextNo("P"),
                order.getOrderId(),
                order.getPayAmount(),
                payChannel,
                "mock://" + payChannel + "/" + order.getOrderId(),
                LocalDateTime.now());

        TradePayOrderAggregate result = new TradePayOrderAggregate();
        result.setTradeOrder(order);
        result.setPayOrder(payOrder);
        return result;
    }

    public void markPaySuccess(TradeOrderEntity order, PayOrderEntity payOrder, String outTradeNo, LocalDateTime payTime) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        if (!StringUtils.hasText(outTradeNo)) {
            throw new AppException("0001", "外部交易单号不能为空");
        }
        payOrder.markSuccess(outTradeNo, payTime);
        order.markPaySuccess(payTime);
    }

    public void closeUnpaidOrder(TradeOrderEntity order, PayOrderEntity payOrder, LocalDateTime closeTime) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        order.close(closeTime);
        payOrder.close();
    }

    public void refundPaidOrder(TradeOrderEntity order, PayOrderEntity payOrder) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        order.refund();
        payOrder.refund();
    }

    private void validateCreateCommand(CreateTradeOrderCommandEntity command) {
        if (command == null) {
            throw new AppException("0001", "订单参数不能为空");
        }
        if (!StringUtils.hasText(command.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(command.getGoodsId())) {
            throw new AppException("0001", "商品编号不能为空");
        }
        if (!StringUtils.hasText(command.getGoodsName())) {
            throw new AppException("0001", "商品名称不能为空");
        }
        if (command.getBuyType() == null) {
            throw new AppException("0001", "购买类型不能为空");
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(command.getBuyType()) && !StringUtils.hasText(command.getActivityId())) {
            throw new AppException("0001", "拼团订单活动编号不能为空");
        }
        if (notPositive(command.getOriginAmount())) {
            throw new AppException("0001", "订单原价必须大于 0");
        }
        if (notPositive(command.getPayAmount())) {
            throw new AppException("0001", "支付金额必须大于 0");
        }
        if (command.getPayAmount().compareTo(command.getOriginAmount()) > 0) {
            throw new AppException("TRADE_0001", "支付金额不能大于订单原价");
        }
    }

    private boolean notPositive(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    private String nextNo(String prefix) {
        String timePart = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + timePart + randomPart;
    }
}
