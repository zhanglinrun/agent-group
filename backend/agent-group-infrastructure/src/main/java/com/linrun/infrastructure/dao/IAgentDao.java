package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.AgentRunPO;
import com.linrun.infrastructure.po.AgentArtifactPO;
import com.linrun.infrastructure.po.AgentFilePO;
import com.linrun.infrastructure.po.AgentLlmInvocationPO;
import com.linrun.infrastructure.po.AgentMessagePO;
import com.linrun.infrastructure.po.AgentSessionPO;
import com.linrun.infrastructure.po.AgentToolInvocationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAgentDao {

    void insertSessionIfAbsent(AgentSessionPO session);

    int updateSession(AgentSessionPO session);

    void insertMessage(AgentMessagePO message);

    List<AgentMessagePO> queryMessages(@Param("userId") String userId, @Param("sessionId") String sessionId);

    AgentSessionPO querySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    List<AgentSessionPO> querySessions(@Param("userId") String userId, @Param("limit") int limit);

    int deleteSession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteMessagesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteFilesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteArtifactsBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    void insertFile(AgentFilePO file);

    AgentFilePO queryFile(@Param("userId") String userId, @Param("fileId") String fileId);

    List<AgentFilePO> queryFiles(@Param("userId") String userId, @Param("limit") int limit);

    List<AgentFilePO> queryFilesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteFile(@Param("userId") String userId, @Param("fileId") String fileId);

    void insertArtifact(AgentArtifactPO artifact);

    List<AgentArtifactPO> queryArtifacts(@Param("userId") String userId, @Param("sessionId") String sessionId);

    void insertRun(AgentRunPO run);

    int updateRunFinish(AgentRunPO run);

    void insertLlmInvocation(AgentLlmInvocationPO invocation);

    int updateLlmInvocationFinish(AgentLlmInvocationPO invocation);

    void insertToolInvocation(AgentToolInvocationPO invocation);

    int updateToolInvocationFinish(AgentToolInvocationPO invocation);

    AgentRunPO queryRun(@Param("userId") String userId, @Param("runId") String runId);

    AgentRunPO queryRunByRequestId(@Param("userId") String userId, @Param("requestId") String requestId);

    AgentRunPO queryLatestRun(@Param("userId") String userId, @Param("sessionId") String sessionId);

    List<AgentRunPO> queryRuns(@Param("userId") String userId,
                                       @Param("sessionId") String sessionId,
                                       @Param("limit") int limit);

    List<AgentLlmInvocationPO> queryLlmInvocations(@Param("runId") String runId);

    List<AgentToolInvocationPO> queryToolInvocations(@Param("runId") String runId);

    List<AgentArtifactPO> queryArtifactsByRun(@Param("runId") String runId);

    int deleteArtifactsSince(@Param("userId") String userId,
                             @Param("sessionId") String sessionId,
                             @Param("startedAt") LocalDateTime startedAt);

    int deleteLlmInvocationsSince(@Param("userId") String userId,
                                  @Param("sessionId") String sessionId,
                                  @Param("startedAt") LocalDateTime startedAt);

    int deleteToolInvocationsSince(@Param("userId") String userId,
                                   @Param("sessionId") String sessionId,
                                   @Param("startedAt") LocalDateTime startedAt);

    int deleteRunsSince(@Param("userId") String userId,
                        @Param("sessionId") String sessionId,
                        @Param("startedAt") LocalDateTime startedAt);
}















