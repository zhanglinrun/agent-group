package com.linrun.infrastructure.trade.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "agent.group.rabbit", name = "enabled", havingValue = "true")
public class RabbitTradeEventConfiguration {

    public static final String TRADE_EVENT_EXCHANGE = "agent.group.trade.event.exchange";
    public static final String TRADE_EVENT_QUEUE = "agent.group.trade.event.queue";
    public static final String TEAM_SUCCESS_EVENT_QUEUE = "agent.group.trade.event.team-success.queue";
    public static final String REFUND_SUCCESS_EVENT_QUEUE = "agent.group.trade.event.refund-success.queue";
    public static final String TRADE_EVENT_DEAD_LETTER_EXCHANGE = "agent.group.trade.event.dlx";
    public static final String TRADE_EVENT_DEAD_LETTER_QUEUE = "agent.group.trade.event.dlq";
    public static final String TRADE_EVENT_ROUTING_KEY = "trade.event.#";
    public static final String TEAM_SUCCESS_EVENT_ROUTING_KEY = "trade.event.group.group_settled";
    public static final String REFUND_SUCCESS_EVENT_ROUTING_KEY = "trade.event.refund.refund_success";
    public static final String TRADE_EVENT_DEAD_LETTER_ROUTING_KEY = "trade.event.dead";
    public static final String NOTIFY_EVENT_ROUTING_KEY = "agent.group.notify.#";

    @Bean
    public TopicExchange tradeEventExchange() {
        return new TopicExchange(TRADE_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue tradeEventQueue() {
        return QueueBuilder.durable(TRADE_EVENT_QUEUE)
                .deadLetterExchange(TRADE_EVENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(TRADE_EVENT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue teamSuccessEventQueue() {
        return QueueBuilder.durable(TEAM_SUCCESS_EVENT_QUEUE)
                .deadLetterExchange(TRADE_EVENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(TRADE_EVENT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue refundSuccessEventQueue() {
        return QueueBuilder.durable(REFUND_SUCCESS_EVENT_QUEUE)
                .deadLetterExchange(TRADE_EVENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(TRADE_EVENT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding tradeEventBinding(TopicExchange tradeEventExchange,
                                     @Qualifier("tradeEventQueue") Queue tradeEventQueue) {
        return BindingBuilder.bind(tradeEventQueue)
                .to(tradeEventExchange)
                .with(TRADE_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding notifyEventBinding(TopicExchange tradeEventExchange,
                                      @Qualifier("tradeEventQueue") Queue tradeEventQueue) {
        return BindingBuilder.bind(tradeEventQueue)
                .to(tradeEventExchange)
                .with(NOTIFY_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding teamSuccessEventBinding(TopicExchange tradeEventExchange,
                                           @Qualifier("teamSuccessEventQueue") Queue teamSuccessEventQueue) {
        return BindingBuilder.bind(teamSuccessEventQueue)
                .to(tradeEventExchange)
                .with(TEAM_SUCCESS_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding refundSuccessEventBinding(TopicExchange tradeEventExchange,
                                             @Qualifier("refundSuccessEventQueue") Queue refundSuccessEventQueue) {
        return BindingBuilder.bind(refundSuccessEventQueue)
                .to(tradeEventExchange)
                .with(REFUND_SUCCESS_EVENT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange tradeEventDeadLetterExchange() {
        return new DirectExchange(TRADE_EVENT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue tradeEventDeadLetterQueue() {
        return QueueBuilder.durable(TRADE_EVENT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding tradeEventDeadLetterBinding(DirectExchange tradeEventDeadLetterExchange,
                                               @Qualifier("tradeEventDeadLetterQueue") Queue tradeEventDeadLetterQueue) {
        return BindingBuilder.bind(tradeEventDeadLetterQueue)
                .to(tradeEventDeadLetterExchange)
                .with(TRADE_EVENT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter tradeEventMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
