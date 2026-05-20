package com.linrun.domain.conversation.adapter;

import com.linrun.domain.conversation.model.GuideConversationMessage;

import java.util.List;

public interface GuideConversationRepository {

    List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit);

    void appendMessage(String sessionId, GuideConversationMessage message);
}
