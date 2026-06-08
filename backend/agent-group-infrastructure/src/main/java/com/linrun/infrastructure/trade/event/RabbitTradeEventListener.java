package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RabbitTradeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitTradeEventListener.class);
    private static final int MAX_RETRY_COUNT = 4;

    private final TradeEventConsumeRecordRepository tradeEventConsumeRecordRepository;

    public RabbitTradeEventListener(TradeEventConsumeRecordRepository tradeEventConsumeRecordRepository) {
        this.tradeEventConsumeRecordRepository = tradeEventConsumeRecordRepository;
    }

    @RabbitListener(queues = RabbitTradeEventConfiguration.TRADE_EVENT_QUEUE)
    public void consume(TradeEventMessageEntity message) {
        if (!hasEventId(message)) {
            return;
        }
        TradeEventConsumeRecordEntity record = TradeEventConsumeRecordEntity.fromMessage(message);
        tradeEventConsumeRecordRepository.save(record);
        TradeEventConsumeRecordEntity current = tradeEventConsumeRecordRepository.queryByEventId(record.getEventId())
                .orElse(record);
        if (current.getConsumeStatus() != null
                && current.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_CONSUMED) {
            LOGGER.info("trade event duplicate skipped, eventId={}, routingKey={}, eventType={}, orderId={}, bizId={}",
                    current.getEventId(),
                    current.getRoutingKey(),
                    current.getEventType(),
                    current.getOrderId(),
                    current.getBizId());
            return;
        }
        if (tradeEventConsumeRecordRepository.updateStatusProcessing(current) != 1) {
            LOGGER.info("trade event busy or already handled, eventId={}, routingKey={}, eventType={}, orderId={}, bizId={}",
                    current.getEventId(),
                    current.getRoutingKey(),
                    current.getEventType(),
                    current.getOrderId(),
                    current.getBizId());
            return;
        }
        try {
            LOGGER.info("trade event consumed, eventId={}, routingKey={}, eventType={}, orderId={}, bizId={}",
                    current.getEventId(),
                    current.getRoutingKey(),
                    current.getEventType(),
                    current.getOrderId(),
                    current.getBizId());
            tradeEventConsumeRecordRepository.updateStatusConsumed(current);
        } catch (Exception e) {
            current.setLastError(trimError(e));
            if (shouldMarkDeadLetter(current)) {
                tradeEventConsumeRecordRepository.updateStatusDeadLetter(current);
            } else {
                tradeEventConsumeRecordRepository.updateStatusRetry(current);
            }
            throw e;
        }
    }

    @RabbitListener(queues = RabbitTradeEventConfiguration.TRADE_EVENT_DEAD_LETTER_QUEUE)
    public void consumeDeadLetter(TradeEventMessageEntity message) {
        if (!hasEventId(message)) {
            return;
        }
        TradeEventConsumeRecordEntity record = TradeEventConsumeRecordEntity.fromMessage(message);
        tradeEventConsumeRecordRepository.save(record);
        TradeEventConsumeRecordEntity current = tradeEventConsumeRecordRepository.queryByEventId(record.getEventId())
                .orElse(record);
        current.setLastError("dead letter");
        if (tradeEventConsumeRecordRepository.updateStatusDeadLetter(current) == 1) {
            LOGGER.warn("trade event moved to dead letter queue, eventId={}, routingKey={}, eventType={}, orderId={}, bizId={}",
                    current.getEventId(),
                    current.getRoutingKey(),
                    current.getEventType(),
                    current.getOrderId(),
                    current.getBizId());
        }
    }

    private boolean hasEventId(TradeEventMessageEntity message) {
        return message != null && StringUtils.hasText(message.getFlowId());
    }

    private boolean shouldMarkDeadLetter(TradeEventConsumeRecordEntity record) {
        return record.getConsumeCount() != null && record.getConsumeCount() >= MAX_RETRY_COUNT;
    }

    private String trimError(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }
}
