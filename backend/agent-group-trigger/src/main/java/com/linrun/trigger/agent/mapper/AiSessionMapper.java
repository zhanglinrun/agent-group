package com.linrun.trigger.agent.mapper;

import com.linrun.trigger.agent.entity.AiSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI会话 Mapper 接口
 */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSession> {

    /**
     * 分页查询会话列表
     */
    @Select("""
            SELECT s1.* FROM ai_session s1
            WHERE s1.id = (SELECT s2.id FROM ai_session s2 WHERE s2.session_id = s1.session_id ORDER BY s2.create_time ASC LIMIT 1)
            ORDER BY s1.update_time DESC
            """)
    IPage<AiSession> selectSessionListWithFirstRecord(Page<AiSession> page);

    @Select("""
            SELECT s1.*
            FROM ai_session s1
            JOIN (
                SELECT session_id, MIN(id) AS first_id, MAX(update_time) AS latest_update_time
                FROM ai_session
                WHERE session_id LIKE CONCAT(#{sessionPrefix}, '%')
                GROUP BY session_id
            ) grouped ON grouped.first_id = s1.id
            ORDER BY grouped.latest_update_time DESC
            LIMIT #{offset}, #{limit}
            """)
    List<AiSession> selectSessionListWithFirstRecordByPrefix(@Param("sessionPrefix") String sessionPrefix,
                                                             @Param("offset") int offset,
                                                             @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT session_id)
            FROM ai_session
            WHERE session_id LIKE CONCAT(#{sessionPrefix}, '%')
            """)
    long countSessionByPrefix(@Param("sessionPrefix") String sessionPrefix);
}















