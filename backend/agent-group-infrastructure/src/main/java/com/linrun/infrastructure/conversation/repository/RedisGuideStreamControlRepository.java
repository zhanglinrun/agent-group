package com.linrun.infrastructure.conversation.repository;

import com.linrun.domain.conversation.adapter.GuideStreamControlRepository;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RedisGuideStreamControlRepository implements GuideStreamControlRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGuideStreamControlRepository.class);
    private static final Duration STOP_TTL = Duration.ofMinutes(10);

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final Map<String, Boolean> fallbackStops = new ConcurrentHashMap<>();

    public RedisGuideStreamControlRepository(RedissonClient redissonClient,
                                             @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public void markStopped(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            redissonClient.<String>getBucket(key(sessionId)).set("1", STOP_TTL);
            redissonClient.getTopic(stopTopic()).publish(sessionId);
        } catch (Exception e) {
            LOGGER.warn("redis stop flag fallback, reason={}", e.getClass().getSimpleName());
            fallbackStops.put(sessionId, true);
        }
    }

    @Override
    public boolean isStopped(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        try {
            return redissonClient.getBucket(key(sessionId)).isExists();
        } catch (Exception e) {
            LOGGER.warn("redis stop flag query fallback, reason={}", e.getClass().getSimpleName());
            return Boolean.TRUE.equals(fallbackStops.get(sessionId));
        }
    }

    @Override
    public void clearStopped(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            redissonClient.getBucket(key(sessionId)).delete();
        } catch (Exception e) {
            LOGGER.warn("redis stop flag clear fallback, reason={}", e.getClass().getSimpleName());
            fallbackStops.remove(sessionId);
        }
    }

    private String key(String sessionId) {
        return keyPrefix + ":guide:stop:" + sessionId;
    }

    private String stopTopic() {
        return keyPrefix + ":guide:stop:topic";
    }
}
