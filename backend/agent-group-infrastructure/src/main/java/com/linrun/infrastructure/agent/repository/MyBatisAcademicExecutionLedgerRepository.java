package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.academic.ledger.adapter.AcademicExecutionLedgerRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.infrastructure.agent.converter.AcademicPOConverter;
import com.linrun.infrastructure.dao.IAcademicAgentDao;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAcademicExecutionLedgerRepository implements AcademicExecutionLedgerRepository {

    private final IAcademicAgentDao academicAgentDao;

    public MyBatisAcademicExecutionLedgerRepository(IAcademicAgentDao academicAgentDao) {
        this.academicAgentDao = academicAgentDao;
    }

    @Override
    public void createRun(AcademicAgentRun run) {
        academicAgentDao.insertRun(AcademicPOConverter.toPO(run));
    }

    @Override
    public void finishRun(AcademicAgentRun run) {
        academicAgentDao.updateRunFinish(AcademicPOConverter.toPO(run));
    }

    @Override
    public void createLlmInvocation(AcademicLlmInvocation invocation) {
        academicAgentDao.insertLlmInvocation(AcademicPOConverter.toPO(invocation));
    }

    @Override
    public void finishLlmInvocation(AcademicLlmInvocation invocation) {
        academicAgentDao.updateLlmInvocationFinish(AcademicPOConverter.toPO(invocation));
    }

    @Override
    public void createToolInvocation(AcademicToolInvocation invocation) {
        academicAgentDao.insertToolInvocation(AcademicPOConverter.toPO(invocation));
    }

    @Override
    public void finishToolInvocation(AcademicToolInvocation invocation) {
        academicAgentDao.updateToolInvocationFinish(AcademicPOConverter.toPO(invocation));
    }

    @Override
    public void saveArtifact(AcademicArtifact artifact) {
        academicAgentDao.insertArtifact(AcademicPOConverter.toPO(artifact));
    }

    @Override
    public Optional<AcademicAgentRun> queryRun(String userId, String runId) {
        return Optional.ofNullable(AcademicPOConverter.toEntity(academicAgentDao.queryRun(userId, runId)));
    }

    @Override
    public Optional<AcademicAgentRun> queryRunByRequestId(String userId, String requestId) {
        return Optional.ofNullable(AcademicPOConverter.toEntity(academicAgentDao.queryRunByRequestId(userId, requestId)));
    }

    @Override
    public Optional<AcademicAgentRun> queryLatestRun(String userId, String sessionId) {
        return Optional.ofNullable(AcademicPOConverter.toEntity(academicAgentDao.queryLatestRun(userId, sessionId)));
    }

    @Override
    public List<AcademicAgentRun> queryRuns(String userId, String sessionId, int limit) {
        return AcademicPOConverter.toRuns(academicAgentDao.queryRuns(userId, sessionId, limit));
    }

    @Override
    public List<AcademicLlmInvocation> queryLlmInvocations(String runId) {
        return AcademicPOConverter.toLlmInvocations(academicAgentDao.queryLlmInvocations(runId));
    }

    @Override
    public List<AcademicToolInvocation> queryToolInvocations(String runId) {
        return AcademicPOConverter.toToolInvocations(academicAgentDao.queryToolInvocations(runId));
    }

    @Override
    public List<AcademicArtifact> queryArtifactsByRun(String runId) {
        return AcademicPOConverter.toArtifacts(academicAgentDao.queryArtifactsByRun(runId));
    }

    @Override
    public int deleteSessionRunsSince(String userId, String sessionId, LocalDateTime startedAt) {
        int count = 0;
        count += academicAgentDao.deleteArtifactsSince(userId, sessionId, startedAt);
        count += academicAgentDao.deleteLlmInvocationsSince(userId, sessionId, startedAt);
        count += academicAgentDao.deleteToolInvocationsSince(userId, sessionId, startedAt);
        count += academicAgentDao.deleteRunsSince(userId, sessionId, startedAt);
        return count;
    }
}















