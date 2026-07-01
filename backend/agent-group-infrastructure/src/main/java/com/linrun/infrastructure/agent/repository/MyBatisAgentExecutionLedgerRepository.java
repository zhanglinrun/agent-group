package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.ledger.adapter.AgentExecutionLedgerRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentLlmInvocation;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.infrastructure.agent.converter.AgentLedgerPOConverter;
import com.linrun.infrastructure.dao.IAgentDao;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAgentExecutionLedgerRepository implements AgentExecutionLedgerRepository {

    private final IAgentDao agentDao;

    public MyBatisAgentExecutionLedgerRepository(IAgentDao agentDao) {
        this.agentDao = agentDao;
    }

    @Override
    public void createRun(AgentRun run) {
        agentDao.insertRun(AgentLedgerPOConverter.toPO(run));
    }

    @Override
    public void finishRun(AgentRun run) {
        agentDao.updateRunFinish(AgentLedgerPOConverter.toPO(run));
    }

    @Override
    public void createLlmInvocation(AgentLlmInvocation invocation) {
        agentDao.insertLlmInvocation(AgentLedgerPOConverter.toPO(invocation));
    }

    @Override
    public void finishLlmInvocation(AgentLlmInvocation invocation) {
        agentDao.updateLlmInvocationFinish(AgentLedgerPOConverter.toPO(invocation));
    }

    @Override
    public void createToolInvocation(AgentToolInvocation invocation) {
        agentDao.insertToolInvocation(AgentLedgerPOConverter.toPO(invocation));
    }

    @Override
    public void finishToolInvocation(AgentToolInvocation invocation) {
        agentDao.updateToolInvocationFinish(AgentLedgerPOConverter.toPO(invocation));
    }

    @Override
    public void saveArtifact(AgentArtifact artifact) {
        agentDao.insertArtifact(AgentLedgerPOConverter.toPO(artifact));
    }

    @Override
    public Optional<AgentRun> queryRun(String userId, String runId) {
        return Optional.ofNullable(AgentLedgerPOConverter.toEntity(agentDao.queryRun(userId, runId)));
    }

    @Override
    public Optional<AgentRun> queryRunByRequestId(String userId, String requestId) {
        return Optional.ofNullable(AgentLedgerPOConverter.toEntity(agentDao.queryRunByRequestId(userId, requestId)));
    }

    @Override
    public Optional<AgentRun> queryLatestRun(String userId, String sessionId) {
        return Optional.ofNullable(AgentLedgerPOConverter.toEntity(agentDao.queryLatestRun(userId, sessionId)));
    }

    @Override
    public List<AgentRun> queryRuns(String userId, String sessionId, int limit) {
        return AgentLedgerPOConverter.toRuns(agentDao.queryRuns(userId, sessionId, limit));
    }

    @Override
    public List<AgentLlmInvocation> queryLlmInvocations(String runId) {
        return AgentLedgerPOConverter.toLlmInvocations(agentDao.queryLlmInvocations(runId));
    }

    @Override
    public List<AgentToolInvocation> queryToolInvocations(String runId) {
        return AgentLedgerPOConverter.toToolInvocations(agentDao.queryToolInvocations(runId));
    }

    @Override
    public List<AgentArtifact> queryArtifactsByRun(String runId) {
        return AgentLedgerPOConverter.toArtifacts(agentDao.queryArtifactsByRun(runId));
    }

    @Override
    public int deleteSessionRunsSince(String userId, String sessionId, LocalDateTime startedAt) {
        int count = 0;
        count += agentDao.deleteArtifactsSince(userId, sessionId, startedAt);
        count += agentDao.deleteLlmInvocationsSince(userId, sessionId, startedAt);
        count += agentDao.deleteToolInvocationsSince(userId, sessionId, startedAt);
        count += agentDao.deleteRunsSince(userId, sessionId, startedAt);
        return count;
    }
}















