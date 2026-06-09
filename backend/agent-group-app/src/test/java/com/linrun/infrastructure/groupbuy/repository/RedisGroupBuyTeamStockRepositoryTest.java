package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.infrastructure.groupbuy.repository.RedisGroupBuyTeamStockRepository;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisGroupBuyTeamStockRepositoryTest {

    @Test
    void shouldCountTeamCreatorWhenCheckingExistingTeamJoin() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong recoveryCounter = mock(RAtomicLong.class);
        RAtomicLong stockCounter = mock(RAtomicLong.class);
        @SuppressWarnings("unchecked")
        RBucket<String> slotBucket = mock(RBucket.class);
        when(redissonClient.getAtomicLong("test-agent:group:team-stock:A10001:T10001:recovery")).thenReturn(recoveryCounter);
        when(redissonClient.getAtomicLong("test-agent:group:team-stock:A10001:T10001")).thenReturn(stockCounter);
        when(redissonClient.<String>getBucket("test-agent:group:team-stock:A10001:T10001:2")).thenReturn(slotBucket);
        when(recoveryCounter.get()).thenReturn(0L);
        when(stockCounter.incrementAndGet()).thenReturn(1L);
        when(slotBucket.trySet(
                eq("1"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        boolean occupied = repository.occupyTeamStock(
                "A10001",
                "T10001",
                3,
                LocalDateTime.now().plusMinutes(30));

        assertTrue(occupied);
        verify(stockCounter, never()).decrementAndGet();
        verify(slotBucket).trySet(
                eq("1"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRollbackWhenOccupyCountExceedsTargetAndRecoveredSlots() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong recoveryCounter = mock(RAtomicLong.class);
        RAtomicLong stockCounter = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong("test-agent:group:team-stock:A10001:T10001:recovery")).thenReturn(recoveryCounter);
        when(redissonClient.getAtomicLong("test-agent:group:team-stock:A10001:T10001")).thenReturn(stockCounter);
        when(recoveryCounter.get()).thenReturn(0L);
        when(stockCounter.incrementAndGet()).thenReturn(3L);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        boolean occupied = repository.occupyTeamStock(
                "A10001",
                "T10001",
                3,
                LocalDateTime.now().plusMinutes(30));

        assertFalse(occupied);
        verify(stockCounter).decrementAndGet();
    }
}















