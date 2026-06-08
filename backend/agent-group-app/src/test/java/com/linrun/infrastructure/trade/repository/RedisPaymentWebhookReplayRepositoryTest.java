package com.linrun.infrastructure.trade.repository;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("test-agent:payment:webhook:replay:ALIPAY:EVT10001")).thenReturn(bucket);
        when(bucket.trySet("1", Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS))
                .thenReturn(true);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redissonClient, "test-agent");

        boolean locked = repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5));

        assertTrue(locked);
        verify(bucket).trySet(
                "1",
                Duration.ofMinutes(5).toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Test
    void shouldReturnFalseWhenProcessingLockExists() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("test-agent:payment:webhook:replay:ALIPAY:EVT10001")).thenReturn(bucket);
        when(bucket.trySet("1", Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS))
                .thenReturn(false);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redissonClient, "test-agent");

        boolean locked = repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5));

        assertFalse(locked);
    }

    @Test
    void shouldReleaseProcessingLock() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("test-agent:payment:webhook:replay:ALIPAY:EVT10001")).thenReturn(bucket);
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redissonClient, "test-agent");

        repository.releaseProcessingLock("ALIPAY:EVT10001");

        verify(bucket).delete();
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.getBucket("test-agent:payment:webhook:replay:ALIPAY:EVT10001"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisPaymentWebhookReplayRepository repository =
                new RedisPaymentWebhookReplayRepository(redissonClient, "test-agent");

        AppException exception = assertThrows(AppException.class,
                () -> repository.acquireProcessingLock("ALIPAY:EVT10001", Duration.ofMinutes(5)));

        assertEquals("PAY_0015", exception.getCode());
    }
}
