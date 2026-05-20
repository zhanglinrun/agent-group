package com.linrun.infrastructure.conversation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.conversation.model.GuideConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

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
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        RedisGuideConversationRepository repository = new RedisGuideConversationRepository(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new LocalGuideConversationRepository(),
                "test-agent");

        repository.appendMessage("S10001", GuideConversationMessage.user("学生预算有限", ""));

        verify(listOperations).rightPush(eq("test-agent:guide:conversation:S10001"), anyString());
        verify(listOperations).trim("test-agent:guide:conversation:S10001", -20, -1);
        verify(redisTemplate).expire("test-agent:guide:conversation:S10001", Duration.ofHours(6));
    }

    @Test
    void shouldReadRecentMessagesFromRedisList() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GuideConversationMessage message = GuideConversationMessage.assistant("推荐标准版");
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("test-agent:guide:conversation:S10001", -1, -1))
                .thenReturn(List.of(objectMapper.writeValueAsString(message)));
        RedisGuideConversationRepository repository = new RedisGuideConversationRepository(
                redisTemplate,
                objectMapper,
                new LocalGuideConversationRepository(),
                "test-agent");

        List<GuideConversationMessage> messages = repository.queryRecentMessages("S10001", 1);

        assertEquals(1, messages.size());
        assertEquals("推荐标准版", messages.get(0).getContent());
    }
}
