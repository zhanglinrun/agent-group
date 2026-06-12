package com.linrun.infrastructure.groupbuy.repository;

import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisGroupBuyTeamStockRepositoryTest {

    private static final String STOCK_KEY = "test-agent:group:team-stock:A10001:T10001";
    private static final String RELEASED_KEY = STOCK_KEY + ":released";

    @Test
    void shouldCountTeamCreatorWhenCheckingExistingTeamJoin() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong occupiedCounter = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong(STOCK_KEY)).thenReturn(occupiedCounter);
        // 3 人团：队长占 1 个名额，可加入名额为 2，第 1 个加入者应放行
        when(occupiedCounter.incrementAndGet()).thenReturn(1L);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        boolean occupied = repository.occupyTeamStock(
                "A10001", "T10001", 3, LocalDateTime.now().plusMinutes(30));

        assertTrue(occupied);
        verify(occupiedCounter, never()).decrementAndGet();
    }

    @Test
    void shouldRollbackWhenJoinCountExceedsCapacity() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong occupiedCounter = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong(STOCK_KEY)).thenReturn(occupiedCounter);
        // 3 人团可加入名额为 2，第 3 个加入者应回退计数并拒绝
        when(occupiedCounter.incrementAndGet()).thenReturn(3L);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        boolean occupied = repository.occupyTeamStock(
                "A10001", "T10001", 3, LocalDateTime.now().plusMinutes(30));

        assertFalse(occupied);
        verify(occupiedCounter).decrementAndGet();
    }

    @Test
    void singleMemberTeamHasNoJoinCapacity() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        assertFalse(repository.occupyTeamStock(
                "A10001", "T10001", 1, LocalDateTime.now().plusMinutes(30)));
        verifyNoInteractions(redissonClient);
    }

    @Test
    void recoverIsIdempotentPerOrder() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong occupiedCounter = mock(RAtomicLong.class);
        @SuppressWarnings("unchecked")
        RSet<String> releasedOrders = mock(RSet.class);
        when(redissonClient.getAtomicLong(STOCK_KEY)).thenReturn(occupiedCounter);
        when(redissonClient.<String>getSet(RELEASED_KEY)).thenReturn(releasedOrders);
        // 第一次释放成功登记，第二次重复释放被集合判重挡掉
        when(releasedOrders.add("O10001")).thenReturn(true).thenReturn(false);
        when(occupiedCounter.decrementAndGet()).thenReturn(0L);
        RedisGroupBuyTeamStockRepository repository =
                new RedisGroupBuyTeamStockRepository(redissonClient, "test-agent");

        LocalDateTime validEndTime = LocalDateTime.now().plusMinutes(30);
        repository.recoverTeamStock("A10001", "T10001", "O10001", validEndTime);
        repository.recoverTeamStock("A10001", "T10001", "O10001", validEndTime);

        verify(occupiedCounter, times(1)).decrementAndGet();
    }
}
