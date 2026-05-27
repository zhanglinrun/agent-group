package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalGuideConversationRepositoryTest {

    @Test
    void shouldKeepRecentMessagesBySession() {
        LocalGuideConversationRepository repository = new LocalGuideConversationRepository();
        repository.appendMessage("S10001", GuideConversationMessage.user("第一轮", ""));
        repository.appendMessage("S10001", GuideConversationMessage.assistant("第一轮回答"));
        repository.appendMessage("S10002", GuideConversationMessage.user("其他会话", ""));

        List<GuideConversationMessage> messages = repository.queryRecentMessages("S10001", 1);

        assertEquals(1, messages.size());
        assertEquals("第一轮回答", messages.get(0).getContent());
    }
}
