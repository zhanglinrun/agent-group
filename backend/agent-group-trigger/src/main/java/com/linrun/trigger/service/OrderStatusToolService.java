package com.linrun.trigger.service;

import com.linrun.api.agent.response.OrderDeltaDTO;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderStatusToolService {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)(?:订单|orderId|order)[^A-Za-z0-9]{0,8}([A-Z][A-Z0-9_-]{3,39})");

    private final TradeOrderRepository tradeOrderRepository;

    public OrderStatusToolService(TradeOrderRepository tradeOrderRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
    }

    public boolean isOrderQuery(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.toLowerCase();
        return normalized.contains("订单")
                || normalized.contains("支付状态")
                || normalized.contains("物流")
                || normalized.contains("order");
    }

    public OrderDeltaDTO queryOrderStatusByQuestion(String question, String userId) {
        return queryOrderStatus(extractOrderId(question), userId);
    }

    public OrderDeltaDTO queryOrderStatus(String orderId, String userId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        if (StringUtils.hasText(userId) && !userId.equals(tradeOrder.getUserId())) {
            throw new AppException("TRADE_0018", "只能查询当前用户的订单");
        }
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId).orElse(null);

        OrderDeltaDTO dto = new OrderDeltaDTO();
        dto.setOrderNo(tradeOrder.getOrderId());
        dto.setTradeType(tradeOrder.getBuyType() == null ? "" : tradeOrder.getBuyType().name());
        dto.setStatus(tradeOrder.getOrderStatus() == null ? "" : tradeOrder.getOrderStatus().name());
        dto.setCurrentStatus(dto.getStatus());
        dto.setDisplayStatus(displayStatus(tradeOrder.getOrderStatus(), payOrder));
        dto.setMessage(buildMessage(tradeOrder, payOrder, dto.getDisplayStatus()));
        return dto;
    }

    public String extractOrderId(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        Matcher matcher = ORDER_ID_PATTERN.matcher(question);
        String orderId = "";
        while (matcher.find()) {
            orderId = matcher.group(1);
        }
        if (StringUtils.hasText(orderId)) {
            return orderId;
        }
        Matcher fallback = Pattern.compile("(O[A-Z0-9_-]{4,39})", Pattern.CASE_INSENSITIVE).matcher(question);
        while (fallback.find()) {
            orderId = fallback.group(1);
        }
        return StringUtils.hasText(orderId) ? orderId.toUpperCase(Locale.ROOT) : "";
    }

    private String displayStatus(TradeOrderStatusEnumVO orderStatus, PayOrderEntity payOrder) {
        if (orderStatus == null) {
            return "未知";
        }
        return switch (orderStatus) {
            case CREATE -> "待创建支付单";
            case PAY_WAIT -> "待支付";
            case PAY_SUCCESS -> "已支付";
            case GROUP_SETTLED -> "拼团已成团";
            case DEAL_DONE -> "交易已完成";
            case CLOSED -> "已关闭";
            case WAIT_REFUND -> "退款处理中";
            case REFUNDED -> "已退款";
        };
    }

    private String buildMessage(TradeOrderEntity tradeOrder, PayOrderEntity payOrder, String displayStatus) {
        StringBuilder builder = new StringBuilder("订单").append(tradeOrder.getOrderId())
                .append("当前状态为").append(displayStatus);
        if (payOrder != null) {
            builder.append("，支付状态为")
                    .append(payOrder.getPayStatus() == null ? "未知" : payOrder.getPayStatus().name());
        }
        return builder.toString();
    }
}
