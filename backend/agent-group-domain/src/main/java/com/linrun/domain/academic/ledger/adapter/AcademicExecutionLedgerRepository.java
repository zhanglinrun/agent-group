package com.linrun.domain.academic.ledger.adapter;

import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;

import java.util.List;
import java.util.Optional;

public interface AcademicExecutionLedgerRepository {

    void createRun(AcademicAgentRun run);

    void finishRun(AcademicAgentRun run);

    void createLlmInvocation(AcademicLlmInvocation invocation);

    void finishLlmInvocation(AcademicLlmInvocation invocation);

    void createToolInvocation(AcademicToolInvocation invocation);

    void finishToolInvocation(AcademicToolInvocation invocation);

    Optional<AcademicAgentRun> queryRun(String userId, String runId);

    Optional<AcademicAgentRun> queryRunByRequestId(String userId, String requestId);

    Optional<AcademicAgentRun> queryLatestRun(String userId, String sessionId);

    List<AcademicAgentRun> queryRuns(String userId, String sessionId, int limit);

    List<AcademicLlmInvocation> queryLlmInvocations(String runId);

    List<AcademicToolInvocation> queryToolInvocations(String runId);

    List<AcademicArtifact> queryArtifactsByRun(String runId);
}
