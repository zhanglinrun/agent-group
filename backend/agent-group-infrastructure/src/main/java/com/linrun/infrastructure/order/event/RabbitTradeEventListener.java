package com.linrun.infrastructure.order.event;

import com.linrun.domain.order.model.TradeEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RabbitTradeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitTradeEventListener.class);

    @RabbitListener(queues = RabbitTradeEventConfiguration.TRADE_EVENT_QUEUE)
    public void consume(TradeEventMessage message) {
        if (message != null) {
            LOGGER.info("trade event consumed, eventId={}, routingKey={}, eventType={}, orderId={}, bizId={}",
                    message.getFlowId(),
                    message.getRoutingKey(),
                    message.getEventType(),
                    message.getOrderId(),
                    message.getBizId());
        }
    }
}
