package com.linrun.infrastructure.conversation.repository;

import com.linrun.domain.conversation.adapter.GuideStreamControlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RedisGuideStreamControlRepository implements GuideStreamControlRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGuideStreamControlRepository.class);
    private static final Duration STOP_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Map<String, Boolean> fallbackStops = new ConcurrentHashMap<>();

    public RedisGuideStreamControlRepository(StringRedisTemplate redisTemplate,
                                             @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public void markStopped(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(sessionId), "1", STOP_TTL);
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
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(sessionId)));
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
            redisTemplate.delete(key(sessionId));
        } catch (Exception e) {
            LOGGER.warn("redis stop flag clear fallback, reason={}", e.getClass().getSimpleName());
            fallbackStops.remove(sessionId);
        }
    }

    private String key(String sessionId) {
        return keyPrefix + ":guide:stop:" + sessionId;
    }
}
