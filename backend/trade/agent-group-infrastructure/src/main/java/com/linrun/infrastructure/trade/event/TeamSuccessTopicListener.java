package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class TeamSuccessTopicListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamSuccessTopicListener.class);

    private final RabbitTradeEventListener rabbitTradeEventListener;

    public TeamSuccessTopicListener(RabbitTradeEventListener rabbitTradeEventListener) {
        this.rabbitTradeEventListener = rabbitTradeEventListener;
    }

    @RabbitListener(queues = RabbitTradeEventConfiguration.TEAM_SUCCESS_EVENT_QUEUE)
    public void consume(TradeEventMessageEntity message) {
        LOGGER.info("team success topic received, eventId={}, teamId={}, orderId={}",
                message == null ? null : message.getFlowId(),
                message == null ? null : message.getBizId(),
                message == null ? null : message.getOrderId());
        rabbitTradeEventListener.consume(message);
    }
}















