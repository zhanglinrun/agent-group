package com.linrun.domain.trade.service;

import com.linrun.domain.trade.model.CreateTradeOrderCommand;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.model.TradeOrderStatus;
import com.linrun.domain.trade.model.TradePayOrder;
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

    public TradeOrder createOrder(CreateTradeOrderCommand command) {
        validateCreateCommand(command);

        TradeOrder order = new TradeOrder();
        order.setOrderId(nextNo("O"));
        order.setUserId(command.getUserId());
        order.setGoodsId(command.getGoodsId());
        order.setGoodsName(command.getGoodsName());
        order.setActivityId(command.getActivityId());
        order.setBuyType(command.getBuyType());
        order.setOriginAmount(command.getOriginAmount());
        order.setPayAmount(command.getPayAmount());
        order.setOrderStatus(TradeOrderStatus.CREATE);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    public TradePayOrder createPayOrder(TradeOrder order, String payChannel) {
        if (!StringUtils.hasText(payChannel)) {
            throw new AppException("0001", "支付渠道不能为空");
        }
        order.waitPay();

        PayOrder payOrder = PayOrder.waitPay(
                nextNo("P"),
                order.getOrderId(),
                order.getPayAmount(),
                payChannel,
                "mock://" + payChannel + "/" + order.getOrderId(),
                LocalDateTime.now());

        TradePayOrder result = new TradePayOrder();
        result.setTradeOrder(order);
        result.setPayOrder(payOrder);
        return result;
    }

    public void markPaySuccess(TradeOrder order, PayOrder payOrder, String outTradeNo, LocalDateTime payTime) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        if (!StringUtils.hasText(outTradeNo)) {
            throw new AppException("0001", "外部交易单号不能为空");
        }
        payOrder.markSuccess(outTradeNo, payTime);
        order.markPaySuccess(payTime);
    }

    public void closeUnpaidOrder(TradeOrder order, PayOrder payOrder, LocalDateTime closeTime) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        order.close(closeTime);
        payOrder.close();
    }

    public void refundPaidOrder(TradeOrder order, PayOrder payOrder) {
        if (!order.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("TRADE_0012", "订单和支付单不匹配");
        }
        order.refund();
        payOrder.refund();
    }

    private void validateCreateCommand(CreateTradeOrderCommand command) {
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
        if (TradeBuyType.GROUP_BUY.equals(command.getBuyType()) && !StringUtils.hasText(command.getActivityId())) {
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
