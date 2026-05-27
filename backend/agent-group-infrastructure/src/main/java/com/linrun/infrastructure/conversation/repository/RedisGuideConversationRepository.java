package com.linrun.infrastructure.conversation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.conversation.model.GuideConversationMessage;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
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

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final LocalGuideConversationRepository fallbackRepository;
    private final GuideConversationRepository persistentRepository;
    private final String keyPrefix;

    @Autowired
    public RedisGuideConversationRepository(RedissonClient redissonClient,
                                             DatabaseGuideConversationRepository persistentRepository,
                                             @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this(redissonClient, new ObjectMapper().findAndRegisterModules(), new LocalGuideConversationRepository(),
                persistentRepository, keyPrefix);
    }

    RedisGuideConversationRepository(RedissonClient redissonClient,
                                     ObjectMapper objectMapper,
                                     LocalGuideConversationRepository fallbackRepository,
                                     GuideConversationRepository persistentRepository,
                                     String keyPrefix) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.fallbackRepository = fallbackRepository;
        this.persistentRepository = persistentRepository == null ? fallbackRepository : persistentRepository;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return List.of();
        }
        try {
            RList<String> list = redissonClient.getList(key(sessionId));
            int size = list.size();
            if (size <= 0) {
                return persistentRepository.queryRecentMessages(sessionId, limit);
            }
            int fromIndex = Math.max(0, size - limit);
            List<String> values = list.range(fromIndex, size - 1);
            return values.stream()
                    .map(this::readMessage)
                    .filter(message -> message != null)
                    .toList();
        } catch (Exception e) {
            LOGGER.warn("redis conversation fallback, reason={}", e.getClass().getSimpleName());
            return persistentRepository.queryRecentMessages(sessionId, limit);
        }
    }

    @Override
    public void appendMessage(String sessionId, GuideConversationMessage message) {
        if (!StringUtils.hasText(sessionId) || message == null) {
            return;
        }
        try {
            String key = key(sessionId);
            RList<String> list = redissonClient.getList(key);
            list.add(objectMapper.writeValueAsString(message));
            int size = list.size();
            if (size > MAX_SESSION_MESSAGES) {
                list.trim(size - MAX_SESSION_MESSAGES, size - 1);
            }
            list.expire(SESSION_TTL);
            persistentRepository.appendMessage(sessionId, message);
        } catch (Exception e) {
            LOGGER.warn("redis conversation append fallback, reason={}", e.getClass().getSimpleName());
            persistentRepository.appendMessage(sessionId, message);
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
