package com.linrun.domain.guide.adapter;

import com.linrun.domain.guide.model.GuideConversationMessage;

import java.util.List;

public interface GuideConversationRepository {

    List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit);

    void appendMessage(String sessionId, GuideConversationMessage message);
}
