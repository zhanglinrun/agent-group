package com.linrun.infrastructure.conversation.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisGuideStreamControlRepositoryTest {

    @Test
    void shouldStoreStopFlagInRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisGuideStreamControlRepository repository = new RedisGuideStreamControlRepository(redisTemplate, "test-agent");

        repository.markStopped("S10001");

        verify(valueOperations).set("test-agent:guide:stop:S10001", "1", Duration.ofMinutes(10));
    }

    @Test
    void shouldReadStopFlagFromRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("test-agent:guide:stop:S10001")).thenReturn(true);
        RedisGuideStreamControlRepository repository = new RedisGuideStreamControlRepository(redisTemplate, "test-agent");

        assertTrue(repository.isStopped("S10001"));
    }
}
