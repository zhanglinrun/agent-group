package com.linrun.infrastructure.guide.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.guide.adapter.GuideConversationRepository;
import com.linrun.domain.guide.model.GuideConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Primary
@Repository
public class RedisGuideConversationRepository implements GuideConversationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGuideConversationRepository.class);
    private static final int MAX_SESSION_MESSAGES = 20;
    private static final Duration SESSION_TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LocalGuideConversationRepository fallbackRepository;
    private final String keyPrefix;

    @Autowired
    public RedisGuideConversationRepository(StringRedisTemplate redisTemplate,
                                            @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this(redisTemplate, new ObjectMapper().findAndRegisterModules(), new LocalGuideConversationRepository(),
                keyPrefix);
    }

    RedisGuideConversationRepository(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     LocalGuideConversationRepository fallbackRepository,
                                     String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackRepository = fallbackRepository;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return List.of();
        }
        try {
            List<String> values = redisTemplate.opsForList().range(key(sessionId), -limit, -1);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .map(this::readMessage)
                    .filter(message -> message != null)
                    .toList();
        } catch (Exception e) {
            LOGGER.warn("redis conversation fallback, reason={}", e.getClass().getSimpleName());
            return fallbackRepository.queryRecentMessages(sessionId, limit);
        }
    }

    @Override
    public void appendMessage(String sessionId, GuideConversationMessage message) {
        if (!StringUtils.hasText(sessionId) || message == null) {
            return;
        }
        try {
            String key = key(sessionId);
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            redisTemplate.opsForList().trim(key, -MAX_SESSION_MESSAGES, -1);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            LOGGER.warn("redis conversation append fallback, reason={}", e.getClass().getSimpleName());
            fallbackRepository.appendMessage(sessionId, message);
        }
    }

    private GuideConversationMessage readMessage(String value) {
        try {
            return objectMapper.readValue(value, GuideConversationMessage.class);
        } catch (Exception e) {
            LOGGER.warn("ignore invalid conversation message, reason={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String key(String sessionId) {
        return keyPrefix + ":guide:conversation:" + sessionId;
    }
}
