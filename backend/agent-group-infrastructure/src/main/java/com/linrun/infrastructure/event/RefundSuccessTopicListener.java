package com.linrun.infrastructure.event;

import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RefundSuccessTopicListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundSuccessTopicListener.class);

    private final RabbitTradeEventListener rabbitTradeEventListener;

    public RefundSuccessTopicListener(RabbitTradeEventListener rabbitTradeEventListener) {
        this.rabbitTradeEventListener = rabbitTradeEventListener;
    }

    @RabbitListener(queues = RabbitTradeEventConfiguration.REFUND_SUCCESS_EVENT_QUEUE)
    public void consume(TradeEventMessageEntity message) {
        LOGGER.info("refund success topic received, eventId={}, refundId={}, orderId={}",
                message == null ? null : message.getFlowId(),
                message == null ? null : message.getBizId(),
                message == null ? null : message.getOrderId());
        rabbitTradeEventListener.consume(message);
    }
}
