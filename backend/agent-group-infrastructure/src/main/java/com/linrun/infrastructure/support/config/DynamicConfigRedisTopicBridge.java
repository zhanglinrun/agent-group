package com.linrun.infrastructure.support.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.support.config.event.DynamicConfigChangedEvent;
import com.linrun.domain.support.config.service.DynamicConfigService;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DynamicConfigRedisTopicBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicConfigRedisTopicBridge.class);

    private final RedissonClient redissonClient;
    private final DynamicConfigService dynamicConfigService;
    private final ObjectMapper objectMapper;
    private final String topicName;
    private final String nodeId = UUID.randomUUID().toString();

    public DynamicConfigRedisTopicBridge(ObjectProvider<RedissonClient> redissonClientProvider,
                                         DynamicConfigService dynamicConfigService,
                                         ObjectMapper objectMapper,
                                         @Value("${agent.group.dynamic-config.topic:agent-group:dynamic-config}") String topicName) {
        this.redissonClient = redissonClientProvider.getIfAvailable();
        this.dynamicConfigService = dynamicConfigService;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
    }

    @PostConstruct
    public void subscribe() {
        if (redissonClient == null) {
            LOGGER.warn("dynamic config redis topic disabled, reason=no_redisson_client");
            return;
        }
        RTopic topic = redissonClient.getTopic(topicName);
        topic.addListener(String.class, (channel, message) -> applyRemoteMessage(message));
        LOGGER.info("dynamic config redis topic subscribed, topic={}", topicName);
    }

    @EventListener
    public void publish(DynamicConfigChangedEvent event) {
        if (redissonClient == null || event == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    new TopicMessage(nodeId, event.configKey(), event.configValue()));
            redissonClient.getTopic(topicName).publish(payload);
        } catch (Exception e) {
            LOGGER.warn("dynamic config redis topic publish failed, key={}, reason={}",
                    event.configKey(), e.getClass().getSimpleName());
        }
    }

    private void applyRemoteMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            TopicMessage topicMessage = objectMapper.readValue(message, TopicMessage.class);
            if (nodeId.equals(topicMessage.nodeId())) {
                return;
            }
            dynamicConfigService.applyRemoteConfig(topicMessage.configKey(), topicMessage.configValue());
            LOGGER.info("dynamic config cache refreshed from redis topic, key={}", topicMessage.configKey());
        } catch (Exception e) {
            LOGGER.warn("dynamic config redis topic message ignored, reason={}", e.getClass().getSimpleName());
        }
    }

    private record TopicMessage(String nodeId, String configKey, String configValue) {
    }
}















