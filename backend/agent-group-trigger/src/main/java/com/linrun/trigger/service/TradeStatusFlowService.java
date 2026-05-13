package com.linrun.trigger.service;

import com.linrun.api.trade.response.TradeStatusFlowDTO;
import com.linrun.domain.trade.adapter.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.TradeStatusFlow;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TradeStatusFlowService {

    public static final String BIZ_ORDER = "ORDER";
    public static final String BIZ_PAY = "PAY";
    public static final String BIZ_GROUP = "GROUP";
    public static final String BIZ_REFUND = "REFUND";

    public static final String EVENT_CREATE_DIRECT_ORDER = "CREATE_DIRECT_ORDER";
    public static final String EVENT_CREATE_GROUP_ORDER = "CREATE_GROUP_ORDER";
    public static final String EVENT_CREATE_PAY_ORDER = "CREATE_PAY_ORDER";
    public static final String EVENT_GROUP_LOCKED = "GROUP_LOCKED";
    public static final String EVENT_PAY_SUCCESS = "PAY_SUCCESS";
    public static final String EVENT_GROUP_LOCK_PAID = "GROUP_LOCK_PAID";
    public static final String EVENT_GROUP_SETTLED = "GROUP_SETTLED";
    public static final String EVENT_CLOSE_UNPAID = "CLOSE_UNPAID";
    public static final String EVENT_RELEASE_LOCK = "RELEASE_LOCK";
    public static final String EVENT_REFUNDED = "REFUNDED";
    public static final String EVENT_REFUND_SUCCESS = "REFUND_SUCCESS";
    public static final String EVENT_RELEASE_PAID_LOCK = "RELEASE_PAID_LOCK";

    private static final DateTimeFormatter FLOW_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeStatusFlowRepository tradeStatusFlowRepository;

    public TradeStatusFlowService(TradeStatusFlowRepository tradeStatusFlowRepository) {
        this.tradeStatusFlowRepository = tradeStatusFlowRepository;
    }

    public void record(String orderId,
                       String bizType,
                       String bizId,
                       String eventType,
                       Object fromStatus,
                       Object toStatus,
                       String remark) {
        String from = statusName(fromStatus);
        String to = statusName(toStatus);
        if (!StringUtils.hasText(orderId)
                || !StringUtils.hasText(bizType)
                || !StringUtils.hasText(bizId)
                || !StringUtils.hasText(eventType)
                || !StringUtils.hasText(to)) {
            return;
        }
        if (from != null && from.equals(to)) {
            return;
        }

        TradeStatusFlow flow = TradeStatusFlow.record(
                nextFlowId(),
                orderId,
                bizType,
                bizId,
                eventType,
                from,
                to,
                remark == null ? "" : remark,
                LocalDateTime.now());
        tradeStatusFlowRepository.save(flow);
    }

    public List<TradeStatusFlowDTO> queryByOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("0001", "orderId cannot be blank");
        }
        return tradeStatusFlowRepository.queryByOrderId(orderId).stream()
                .map(this::toDTO)
                .toList();
    }

    private TradeStatusFlowDTO toDTO(TradeStatusFlow flow) {
        TradeStatusFlowDTO dto = new TradeStatusFlowDTO();
        dto.setFlowId(flow.getFlowId());
        dto.setOrderId(flow.getOrderId());
        dto.setBizType(flow.getBizType());
        dto.setBizId(flow.getBizId());
        dto.setEventType(flow.getEventType());
        dto.setFromStatus(flow.getFromStatus());
        dto.setToStatus(flow.getToStatus());
        dto.setRemark(flow.getRemark());
        dto.setCreateTime(flow.getCreateTime());
        return dto;
    }

    private String statusName(Object status) {
        if (status == null) {
            return null;
        }
        if (status instanceof Enum<?> enumStatus) {
            return enumStatus.name();
        }
        return status.toString();
    }

    private String nextFlowId() {
        String timePart = LocalDateTime.now().format(FLOW_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "F" + timePart + randomPart;
    }
}
