package com.linrun.infrastructure.support.repository;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisScheduledJobLockRepositoryTest {

    @Test
    void shouldAcquireJobLockWithTokenAndLeaseTime() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("test-agent:job:lock:group-buy-notify-task")).thenReturn(lock);
        try {
            when(lock.tryLock(0L, 60000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        RedisScheduledJobLockRepository repository =
                new RedisScheduledJobLockRepository(redissonClient, "test-agent");

        Optional<String> token = repository.tryLock("group-buy-notify-task", Duration.ofSeconds(60));

        assertTrue(token.isPresent());
        try {
            verify(lock).tryLock(0L, 60000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldReturnEmptyWhenJobLockIsHeld() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("test-agent:job:lock:group-buy-notify-task")).thenReturn(lock);
        try {
            when(lock.tryLock(0L, 60000L, TimeUnit.MILLISECONDS)).thenReturn(false);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        RedisScheduledJobLockRepository repository =
                new RedisScheduledJobLockRepository(redissonClient, "test-agent");

        Optional<String> token = repository.tryLock("group-buy-notify-task", Duration.ofSeconds(60));

        assertFalse(token.isPresent());
    }

    @Test
    void shouldUnlockCurrentThreadRedissonLock() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("test-agent:job:lock:group-buy-notify-task")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisScheduledJobLockRepository repository =
                new RedisScheduledJobLockRepository(redissonClient, "test-agent");

        repository.unlock("group-buy-notify-task", "token-10001");

        verify(lock).unlock();
    }
}















