package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GuideConversationMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideConversationDao {

    void insertMessage(@Param("sessionId") String sessionId,
                       @Param("message") GuideConversationMessagePO message);

    List<GuideConversationMessagePO> queryRecentMessages(@Param("sessionId") String sessionId,
                                                         @Param("limit") int limit);
}
