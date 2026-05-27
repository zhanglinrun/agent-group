package com.linrun.infrastructure.adapter.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import org.junit.jupiter.api.Test;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisGuideConversationRepositoryTest {

    @Test
    void shouldAppendMessageToRedisListAndKeepTtl() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RList<String> list = mock(RList.class);
        when(redissonClient.<String>getList("test-agent:guide:conversation:S10001")).thenReturn(list);
        when(list.size()).thenReturn(1);
        RedisGuideConversationRepository repository = new RedisGuideConversationRepository(
                redissonClient,
                new ObjectMapper().findAndRegisterModules(),
                new LocalGuideConversationRepository(),
                new LocalGuideConversationRepository(),
                "test-agent");

        repository.appendMessage("S10001", GuideConversationMessage.user("student budget limited", ""));

        verify(list).add(anyString());
        verify(list).expire(Duration.ofHours(6));
    }

    @Test
    void shouldTrimRedisListWhenExceedMaxSessionMessages() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RList<String> list = mock(RList.class);
        when(redissonClient.<String>getList("test-agent:guide:conversation:S10001")).thenReturn(list);
        when(list.size()).thenReturn(21);
        RedisGuideConversationRepository repository = new RedisGuideConversationRepository(
                redissonClient,
                new ObjectMapper().findAndRegisterModules(),
                new LocalGuideConversationRepository(),
                new LocalGuideConversationRepository(),
                "test-agent");

        repository.appendMessage("S10001", GuideConversationMessage.user("need phone", ""));

        verify(list).trim(1, 20);
        verify(list).expire(Duration.ofHours(6));
    }

    @Test
    void shouldReadRecentMessagesFromRedisList() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RList<String> list = mock(RList.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GuideConversationMessage message = GuideConversationMessage.assistant("recommend standard edition");
        when(redissonClient.<String>getList("test-agent:guide:conversation:S10001")).thenReturn(list);
        when(list.size()).thenReturn(1);
        when(list.range(0, 0)).thenReturn(List.of(objectMapper.writeValueAsString(message)));
        RedisGuideConversationRepository repository = new RedisGuideConversationRepository(
                redissonClient,
                objectMapper,
                new LocalGuideConversationRepository(),
                new LocalGuideConversationRepository(),
                "test-agent");

        List<GuideConversationMessage> messages = repository.queryRecentMessages("S10001", 1);

        assertEquals(1, messages.size());
        assertEquals("recommend standard edition", messages.get(0).getContent());
        verify(list).range(eq(0), eq(0));
    }
}
