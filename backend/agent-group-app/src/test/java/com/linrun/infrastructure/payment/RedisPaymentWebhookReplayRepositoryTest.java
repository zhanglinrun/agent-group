package com.linrun.infrastructure.payment;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPaymentWebhookReplayRepositoryTest {

    @Test
    void shouldAcquireProcessingLockByRedisSetIfAbsent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "test-agent:payment:webhook:replay:ALIPAY:EVT10001",
                "1",
                Duration.ofMinutes(5)))
                .thenReturn(true);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redisTemplate, "test-agent");

        boolean locked = repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5));

        assertTrue(locked);
        verify(valueOperations).setIfAbsent(
                "test-agent:payment:webhook:replay:ALIPAY:EVT10001",
                "1",
                Duration.ofMinutes(5));
    }

    @Test
    void shouldReturnFalseWhenProcessingLockExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "test-agent:payment:webhook:replay:ALIPAY:EVT10001",
                "1",
                Duration.ofMinutes(5)))
                .thenReturn(false);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redisTemplate, "test-agent");

        boolean locked = repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5));

        assertFalse(locked);
    }

    @Test
    void shouldReleaseProcessingLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redisTemplate, "test-agent");

        repository.releaseProcessingLock("ALIPAY:EVT10001");

        verify(redisTemplate).delete("test-agent:payment:webhook:replay:ALIPAY:EVT10001");
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redisTemplate, "test-agent");

        AppException exception = assertThrows(AppException.class,
                () -> repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5)));

        assertEquals("PAY_0015", exception.getCode());
    }
}
