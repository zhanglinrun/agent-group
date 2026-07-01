package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.adapter.AgentRepository;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.model.AgentFile;
import com.linrun.domain.agent.model.AgentMessage;
import com.linrun.domain.agent.model.AgentSession;
import com.linrun.infrastructure.agent.converter.AgentLedgerPOConverter;
import com.linrun.infrastructure.dao.IAgentDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAgentRepository implements AgentRepository {

    private final IAgentDao agentDao;

    public MyBatisAgentRepository(IAgentDao agentDao) {
        this.agentDao = agentDao;
    }

    @Override
    public void saveSessionIfAbsent(AgentSession session) {
        agentDao.insertSessionIfAbsent(AgentLedgerPOConverter.toPO(session));
    }

    @Override
    public void updateSession(AgentSession session) {
        agentDao.updateSession(AgentLedgerPOConverter.toPO(session));
    }

    @Override
    public void saveMessage(AgentMessage message) {
        agentDao.insertMessage(AgentLedgerPOConverter.toPO(message));
    }

    @Override
    public List<AgentMessage> queryMessages(String userId, String sessionId) {
        return AgentLedgerPOConverter.toMessages(agentDao.queryMessages(userId, sessionId));
    }

    @Override
    public Optional<AgentSession> querySession(String userId, String sessionId) {
        return Optional.ofNullable(AgentLedgerPOConverter.toEntity(agentDao.querySession(userId, sessionId)));
    }

    @Override
    public List<AgentSession> querySessions(String userId, int limit) {
        return AgentLedgerPOConverter.toSessions(agentDao.querySessions(userId, limit));
    }

    @Override
    public void deleteSession(String userId, String sessionId) {
        agentDao.deleteMessagesBySession(userId, sessionId);
        agentDao.deleteFilesBySession(userId, sessionId);
        agentDao.deleteArtifactsBySession(userId, sessionId);
        agentDao.deleteSession(userId, sessionId);
    }

    @Override
    public void saveFile(AgentFile file) {
        agentDao.insertFile(AgentLedgerPOConverter.toPO(file));
    }

    @Override
    public Optional<AgentFile> queryFile(String userId, String fileId) {
        return Optional.ofNullable(AgentLedgerPOConverter.toEntity(agentDao.queryFile(userId, fileId)));
    }

    @Override
    public List<AgentFile> queryFiles(String userId, int limit) {
        return AgentLedgerPOConverter.toFiles(agentDao.queryFiles(userId, limit));
    }

    @Override
    public List<AgentFile> queryFilesBySession(String userId, String sessionId) {
        return AgentLedgerPOConverter.toFiles(agentDao.queryFilesBySession(userId, sessionId));
    }

    @Override
    public void deleteFile(String userId, String fileId) {
        agentDao.deleteFile(userId, fileId);
    }

    @Override
    public void saveArtifact(AgentArtifact artifact) {
        agentDao.insertArtifact(AgentLedgerPOConverter.toPO(artifact));
    }

    @Override
    public List<AgentArtifact> queryArtifacts(String userId, String sessionId) {
        return AgentLedgerPOConverter.toArtifacts(agentDao.queryArtifacts(userId, sessionId));
    }
}















