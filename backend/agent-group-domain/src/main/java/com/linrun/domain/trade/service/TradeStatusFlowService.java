package com.linrun.domain.trade.service;

import com.linrun.api.dto.TradeStatusFlowDTO;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    public static final String EVENT_CREATE_GATEWAY_PAYMENT = "CREATE_GATEWAY_PAYMENT";
    public static final String EVENT_PAYMENT_WEBHOOK_VERIFIED = "PAYMENT_WEBHOOK_VERIFIED";
    public static final String EVENT_RECONCILE_PAYMENT = "RECONCILE_PAYMENT";

    private static final DateTimeFormatter FLOW_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeStatusFlowRepository tradeStatusFlowRepository;
    private final TradeEventPublisher tradeEventPublisher;

    @Autowired
    public TradeStatusFlowService(TradeStatusFlowRepository tradeStatusFlowRepository,
                                  TradeEventPublisher tradeEventPublisher) {
        this.tradeStatusFlowRepository = tradeStatusFlowRepository;
        this.tradeEventPublisher = tradeEventPublisher == null ? TradeEventPublisher.noop() : tradeEventPublisher;
    }

    public TradeStatusFlowService(TradeStatusFlowRepository tradeStatusFlowRepository) {
        this(tradeStatusFlowRepository, TradeEventPublisher.noop());
    }

    @Transactional(rollbackFor = Exception.class)
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

        TradeStatusFlowEntity flow = TradeStatusFlowEntity.record(
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
        publishEventAfterCommit(TradeEventMessageEntity.fromFlow(flow));
    }

    public List<TradeStatusFlowDTO> queryByOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("0001", "订单编号不能为空");
        }
        return tradeStatusFlowRepository.queryByOrderId(orderId).stream()
                .map(this::toDTO)
                .toList();
    }

    private void publishEventAfterCommit(TradeEventMessageEntity message) {
        if (message == null) {
            return;
        }
        Runnable publishTask = () -> {
            try {
                tradeEventPublisher.publish(message);
            } catch (Exception ignored) {
                // best-effort；可靠性由 XXL-JOB 补偿任务兜底
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }
        publishTask.run();
    }

    private TradeStatusFlowDTO toDTO(TradeStatusFlowEntity flow) {
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
