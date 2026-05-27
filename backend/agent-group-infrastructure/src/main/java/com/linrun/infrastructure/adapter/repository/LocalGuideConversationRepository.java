package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LocalGuideConversationRepository implements GuideConversationRepository {

    private static final int MAX_SESSION_MESSAGES = 20;

    private final Map<String, List<GuideConversationMessage>> conversationStore = new ConcurrentHashMap<>();

    @Override
    public List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return List.of();
        }
        List<GuideConversationMessage> messages = conversationStore.getOrDefault(sessionId, List.of());
        int fromIndex = Math.max(0, messages.size() - limit);
        return List.copyOf(messages.subList(fromIndex, messages.size()));
    }

    @Override
    public void appendMessage(String sessionId, GuideConversationMessage message) {
        if (!StringUtils.hasText(sessionId) || message == null) {
            return;
        }
        conversationStore.compute(sessionId, (key, messages) -> append(messages, message));
    }

    private List<GuideConversationMessage> append(List<GuideConversationMessage> messages,
                                                  GuideConversationMessage message) {
        List<GuideConversationMessage> updated = new ArrayList<>(messages == null ? List.of() : messages);
        updated.add(message);
        if (updated.size() <= MAX_SESSION_MESSAGES) {
            return updated;
        }
        return new ArrayList<>(updated.subList(updated.size() - MAX_SESSION_MESSAGES, updated.size()));
    }
}
