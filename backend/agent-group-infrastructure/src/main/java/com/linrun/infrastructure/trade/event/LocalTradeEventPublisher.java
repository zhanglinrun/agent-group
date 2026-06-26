package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalTradeEventPublisher implements TradeEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTradeEventPublisher.class);

    /**
     * 本地回退发布器：仅记录事件日志，不做任何业务处理。
     * <p>
     * 设计说明：额度发放与退款回滚的权威链路是 {@code PaymentCompletionService.complete} 的同步
     * webhook 事务（支付成功 → 拼团结算 → 发额度），以及 {@code TradeTimeoutCompensationJob} 的
     * 主动查单补偿，不依赖事件消费方。{@code TradeEventOutbox} 本地消息表在这里用于持久化事件、
     * 由 {@code TradeEventOutboxDispatchJob} 推进状态机（INIT→SUCCESS），其 SUCCESS 仅表示事件
     * 已发布/记录，不代表下游业务已执行。如需通过事件异步触发发额度或退款回滚，应在
     * {@code RabbitTradeEventListener}（rabbit.enabled=true 时启用）里按 eventType 补充分发逻辑。
     */
    @Override
    public void publish(TradeEventMessageEntity message) {
        if (message != null) {
            LOGGER.debug("trade event local fallback, eventType={}, orderId={}",
                    message.getEventType(), message.getOrderId());
        }
    }
}















