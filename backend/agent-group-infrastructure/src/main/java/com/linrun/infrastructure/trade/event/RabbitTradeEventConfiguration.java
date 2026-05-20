package com.linrun.infrastructure.trade.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RabbitTradeEventConfiguration {

    public static final String TRADE_EVENT_EXCHANGE = "agent.group.trade.event.exchange";
    public static final String TRADE_EVENT_QUEUE = "agent.group.trade.event.queue";
    public static final String TRADE_EVENT_ROUTING_KEY = "trade.event.#";
    public static final String NOTIFY_EVENT_ROUTING_KEY = "agent.group.notify.#";

    @Bean
    public TopicExchange tradeEventExchange() {
        return new TopicExchange(TRADE_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue tradeEventQueue() {
        return new Queue(TRADE_EVENT_QUEUE, true);
    }

    @Bean
    public Binding tradeEventBinding(TopicExchange tradeEventExchange, Queue tradeEventQueue) {
        return BindingBuilder.bind(tradeEventQueue)
                .to(tradeEventExchange)
                .with(TRADE_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding notifyEventBinding(TopicExchange tradeEventExchange, Queue tradeEventQueue) {
        return BindingBuilder.bind(tradeEventQueue)
                .to(tradeEventExchange)
                .with(NOTIFY_EVENT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter tradeEventMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
