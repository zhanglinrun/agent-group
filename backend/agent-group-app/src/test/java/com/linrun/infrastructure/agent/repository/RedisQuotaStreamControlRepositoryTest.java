package com.linrun.infrastructure.agent.repository;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisQuotaStreamControlRepositoryTest {

    @Test
    void shouldStoreStopFlagInRedis() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.<String>getBucket("test-agent:guide:stop:S10001")).thenReturn(bucket);
        when(redissonClient.getTopic("test-agent:guide:stop:topic")).thenReturn(topic);
        RedisQuotaStreamControlRepository repository = new RedisQuotaStreamControlRepository(redissonClient, "test-agent");

        repository.markStopped("S10001");

        verify(bucket).set("1", Duration.ofMinutes(10));
        verify(topic).publish("S10001");
    }

    @Test
    void shouldReadStopFlagFromRedis() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("test-agent:guide:stop:S10001")).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(true);
        RedisQuotaStreamControlRepository repository = new RedisQuotaStreamControlRepository(redissonClient, "test-agent");

        assertTrue(repository.isStopped("S10001"));
    }
}















