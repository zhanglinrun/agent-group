package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.adapter.TradeEventPublisher;
import com.linrun.domain.trade.model.TradeEventMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

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
        String routingKey = routingKey(message);
        rabbitTemplate.convertAndSend(
                RabbitTradeEventConfiguration.TRADE_EVENT_EXCHANGE,
                routingKey,
                message,
                rabbitMessage -> {
                    rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    rabbitMessage.getMessageProperties().setHeader("eventId", message.getFlowId());
                    rabbitMessage.getMessageProperties().setHeader("routingKey", routingKey);
                    rabbitMessage.getMessageProperties().setHeader("bizType", message.getBizType());
                    rabbitMessage.getMessageProperties().setHeader("eventType", message.getEventType());
                    return rabbitMessage;
                });
    }

    private String routingKey(TradeEventMessage message) {
        if (StringUtils.hasText(message.getRoutingKey())) {
            return message.getRoutingKey();
        }
        String bizType = message.getBizType() == null ? "unknown" : message.getBizType().toLowerCase(Locale.ROOT);
        String eventType = message.getEventType() == null ? "unknown" : message.getEventType().toLowerCase(Locale.ROOT);
        return "trade.event." + bizType + "." + eventType;
    }
}
