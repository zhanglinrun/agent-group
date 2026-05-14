package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.adapter.TradeEventPublisher;
import com.linrun.domain.trade.model.TradeEventMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RabbitTradeEventPublisher implements TradeEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitTradeEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(TradeEventMessage message) {
        if (message == null) {
            return;
        }
        rabbitTemplate.convertAndSend(
                RabbitTradeEventConfiguration.TRADE_EVENT_EXCHANGE,
                routingKey(message),
                message);
    }

    private String routingKey(TradeEventMessage message) {
        String bizType = message.getBizType() == null ? "unknown" : message.getBizType().toLowerCase();
        String eventType = message.getEventType() == null ? "unknown" : message.getEventType().toLowerCase();
        return "trade.event." + bizType + "." + eventType;
    }
}
