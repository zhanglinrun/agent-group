package com.linrun.infrastructure.dao;

import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideConversationDao {

    void insertMessage(@Param("sessionId") String sessionId,
                       @Param("message") GuideConversationMessage message);

    List<GuideConversationMessage> queryRecentMessages(@Param("sessionId") String sessionId,
                                                       @Param("limit") int limit);
}
