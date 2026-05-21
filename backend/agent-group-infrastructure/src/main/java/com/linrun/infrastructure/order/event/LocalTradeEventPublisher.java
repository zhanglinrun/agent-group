package com.linrun.infrastructure.order.event;

import com.linrun.domain.order.adapter.TradeEventPublisher;
import com.linrun.domain.order.model.entity.TradeEventMessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalTradeEventPublisher implements TradeEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTradeEventPublisher.class);

    @Override
    public void publish(TradeEventMessageEntity message) {
        if (message != null) {
            LOGGER.debug("trade event local fallback, eventType={}, orderId={}",
                    message.getEventType(), message.getOrderId());
        }
    }
}
