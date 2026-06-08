package com.linrun.trigger.http.trade;

import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class TradeDisplayStatusResolver {

    private TradeDisplayStatusResolver() {
    }

    static String resolve(TradeOrderEntity order, TradeOrderRepository tradeOrderRepository) {
        if (order == null || order.getOrderStatus() == null) {
            return "-";
        }
        boolean groupOrder = TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType());
        TradeOrderStatusEnumVO orderStatus = order.getOrderStatus();
        if (groupOrder && TradeOrderStatusEnumVO.CLOSED.equals(orderStatus)) {
            return "拼团已关闭，未发放额度";
        }
        if (groupOrder && TradeOrderStatusEnumVO.REFUNDED.equals(orderStatus)) {
            RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(order.getOrderId()).orElse(null);
            if (refundOrder != null && isGroupTimeoutRefund(refundOrder.getRefundReason())) {
                return "拼团未成团，已退款";
            }
            return "已退款，额度已回滚";
        }
        if (groupOrder && TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)) {
            return "支付成功，等待成团";
        }
        return switch (orderStatus) {
            case CREATE -> "待创建支付单";
            case PAY_WAIT -> "待支付";
            case PAY_SUCCESS -> "已支付，额度已到账";
            case GROUP_SETTLED -> "拼团已成团，额度已到账";
            case DEAL_DONE -> "交易完成，额度已到账";
            case CLOSED -> "订单已关闭";
            case WAIT_REFUND -> "退款处理中";
            case REFUNDED -> "已退款，额度已回滚";
        };
    }

    private static boolean isGroupTimeoutRefund(String refundReason) {
        if (!StringUtils.hasText(refundReason)) {
            return false;
        }
        String reason = refundReason.trim().toLowerCase(Locale.ROOT);
        return reason.contains("group buy timeout")
                || reason.contains("timeout unformed")
                || refundReason.contains("拼团超时")
                || refundReason.contains("未成团")
                || refundReason.contains("成团失败");
    }
}
