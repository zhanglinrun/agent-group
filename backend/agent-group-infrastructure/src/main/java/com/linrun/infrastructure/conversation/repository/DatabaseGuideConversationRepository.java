package com.linrun.infrastructure.conversation.repository;

import com.linrun.domain.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.conversation.model.GuideConversationMessage;
import com.linrun.infrastructure.dao.IGuideConversationDao;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class DatabaseGuideConversationRepository implements GuideConversationRepository {

    private final IGuideConversationDao guideConversationDao;

    public DatabaseGuideConversationRepository(IGuideConversationDao guideConversationDao) {
        this.guideConversationDao = guideConversationDao;
    }

    @Override
    public List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return List.of();
        }
        return guideConversationDao.queryRecentMessages(sessionId, Math.max(1, limit));
    }

    @Override
    public void appendMessage(String sessionId, GuideConversationMessage message) {
        if (!StringUtils.hasText(sessionId) || message == null) {
            return;
        }
        guideConversationDao.insertMessage(sessionId, message);
    }
}
