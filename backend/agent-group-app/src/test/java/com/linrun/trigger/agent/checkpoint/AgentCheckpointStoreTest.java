package com.linrun.trigger.agent.checkpoint;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 断点续跑存储层：验证 Redis key 规约、TTL 写入、读取往返与故障降级（不拖垮主链路）。
 * 用 mock RedissonClient，避免测试依赖真实 Redis。
 */
class AgentCheckpointStoreTest {

    @Test
    void saveSerializesAndStoresUnderContinueTraceIdKeyWithTtl() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket("agent:checkpoint:ckpt-1")).thenReturn(bucket);

        AgentCheckpointStore store = new AgentCheckpointStore(client, 24);
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setContinueTraceId("ckpt-1");
        checkpoint.setAgentType("data");

        store.save(checkpoint);

        verify(bucket).set(anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
        assertTrue(checkpoint.getSavedAt() > 0, "save 应自动盖时间戳");
    }

    @Test
    void loadReturnsEmptyWhenBucketMissing() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        AgentCheckpointStore store = new AgentCheckpointStore(client, 24);
        Optional<AgentCheckpoint> loaded = store.load("ckpt-missing");

        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadRestoresCheckpointFromSerializedJson() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket("agent:checkpoint:ckpt-2")).thenReturn(bucket);

        AgentCheckpointStore store = new AgentCheckpointStore(client, 24);
        AgentCheckpoint original = new AgentCheckpoint();
        original.setContinueTraceId("ckpt-2");
        original.setRound(5);
        original.setAgentType("trade-diagnosis");
        when(bucket.get()).thenReturn(new AgentCheckpointSerializer().toJson(original));

        Optional<AgentCheckpoint> loaded = store.load("ckpt-2");

        assertTrue(loaded.isPresent());
        assertEquals("ckpt-2", loaded.get().getContinueTraceId());
        assertEquals(5, loaded.get().getRound());
        assertEquals("trade-diagnosis", loaded.get().getAgentType());
    }

    @Test
    void clearDeletesBucket() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket("agent:checkpoint:ckpt-3")).thenReturn(bucket);

        AgentCheckpointStore store = new AgentCheckpointStore(client, 24);
        store.clear("ckpt-3");

        verify(bucket).delete();
    }

    @Test
    void loadSwallowsRedisErrorsAndReturnsEmpty() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenThrow(new RuntimeException("redis down"));

        AgentCheckpointStore store = new AgentCheckpointStore(client, 24);
        // 降级：Redis 故障时不抛异常，按无快照处理，保证主执行链路不受影响
        Optional<AgentCheckpoint> loaded = store.load("ckpt-x");

        assertTrue(loaded.isEmpty());
    }
}
