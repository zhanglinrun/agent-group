package com.linrun.infrastructure.agent.repository;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAgentStreamTaskRegistryTest {

    @Test
    void shouldCancelLocalTaskWhenReceiveStopTopicMessage() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        MessageListener<String>[] listenerHolder = new MessageListener[1];
        when(redissonClient.getTopic("test-agent:guide:stop:topic")).thenReturn(topic);
        when(redissonClient.<String>getBucket("test-agent:guide:task:S10001")).thenReturn(bucket);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenAnswer(invocation -> {
            listenerHolder[0] = invocation.getArgument(1);
            return 7;
        });
        when(bucket.trySet(anyString(), eq(Duration.ofMinutes(30).toMillis()), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    when(bucket.get()).thenReturn(invocation.getArgument(0));
                    return true;
                });
        RedisAgentStreamTaskRegistry registry = new RedisAgentStreamTaskRegistry(redissonClient, "test-agent");
        registry.afterPropertiesSet();

        boolean registered = registry.register("S10001", "REQ10001");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        registry.bind("S10001", "REQ10001", () -> cancelled.set(true));

        listenerHolder[0].onMessage("test-agent:guide:stop:topic", "S10001");
        registry.destroy();

        assertTrue(registered);
        assertTrue(cancelled.get());
        verify(bucket).delete();
        verify(topic).removeListener(7);
    }

    @Test
    void shouldRejectWhenRemoteTaskAlreadyExists() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.getTopic("test-agent:guide:stop:topic")).thenReturn(topic);
        when(redissonClient.<String>getBucket("test-agent:guide:task:S10001")).thenReturn(bucket);
        when(bucket.trySet(anyString(), eq(Duration.ofMinutes(30).toMillis()), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(false);
        RedisAgentStreamTaskRegistry registry = new RedisAgentStreamTaskRegistry(redissonClient, "test-agent");

        boolean registered = registry.register("S10001", "REQ10001");

        assertFalse(registered);
    }
}
