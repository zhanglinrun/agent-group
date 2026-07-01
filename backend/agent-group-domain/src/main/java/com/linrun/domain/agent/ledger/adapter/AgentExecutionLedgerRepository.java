package com.linrun.domain.agent.ledger.adapter;

import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentLlmInvocation;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentExecutionLedgerRepository {

    void createRun(AgentRun run);

    void finishRun(AgentRun run);

    void createLlmInvocation(AgentLlmInvocation invocation);

    void finishLlmInvocation(AgentLlmInvocation invocation);

    void createToolInvocation(AgentToolInvocation invocation);

    void finishToolInvocation(AgentToolInvocation invocation);

    void saveArtifact(AgentArtifact artifact);

    Optional<AgentRun> queryRun(String userId, String runId);

    Optional<AgentRun> queryRunByRequestId(String userId, String requestId);

    Optional<AgentRun> queryLatestRun(String userId, String sessionId);

    List<AgentRun> queryRuns(String userId, String sessionId, int limit);

    List<AgentLlmInvocation> queryLlmInvocations(String runId);

    List<AgentToolInvocation> queryToolInvocations(String runId);

    List<AgentArtifact> queryArtifactsByRun(String runId);

    default int deleteSessionRunsSince(String userId, String sessionId, LocalDateTime startedAt) {
        return 0;
    }
}















