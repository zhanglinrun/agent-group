package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.infrastructure.agent.converter.AcademicPOConverter;
import com.linrun.infrastructure.dao.IAcademicAgentDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAcademicAgentRepository implements AcademicAgentRepository {

    private final IAcademicAgentDao academicAgentDao;

    public MyBatisAcademicAgentRepository(IAcademicAgentDao academicAgentDao) {
        this.academicAgentDao = academicAgentDao;
    }

    @Override
    public void saveSessionIfAbsent(AcademicSession session) {
        academicAgentDao.insertSessionIfAbsent(AcademicPOConverter.toPO(session));
    }

    @Override
    public void updateSession(AcademicSession session) {
        academicAgentDao.updateSession(AcademicPOConverter.toPO(session));
    }

    @Override
    public void saveMessage(AcademicMessage message) {
        academicAgentDao.insertMessage(AcademicPOConverter.toPO(message));
    }

    @Override
    public List<AcademicMessage> queryMessages(String userId, String sessionId) {
        return AcademicPOConverter.toMessages(academicAgentDao.queryMessages(userId, sessionId));
    }

    @Override
    public Optional<AcademicSession> querySession(String userId, String sessionId) {
        return Optional.ofNullable(AcademicPOConverter.toEntity(academicAgentDao.querySession(userId, sessionId)));
    }

    @Override
    public List<AcademicSession> querySessions(String userId, int limit) {
        return AcademicPOConverter.toSessions(academicAgentDao.querySessions(userId, limit));
    }

    @Override
    public void deleteSession(String userId, String sessionId) {
        academicAgentDao.deleteMessagesBySession(userId, sessionId);
        academicAgentDao.deleteFilesBySession(userId, sessionId);
        academicAgentDao.deleteArtifactsBySession(userId, sessionId);
        academicAgentDao.deleteSession(userId, sessionId);
    }

    @Override
    public void saveFile(AcademicFile file) {
        academicAgentDao.insertFile(AcademicPOConverter.toPO(file));
    }

    @Override
    public Optional<AcademicFile> queryFile(String userId, String fileId) {
        return Optional.ofNullable(AcademicPOConverter.toEntity(academicAgentDao.queryFile(userId, fileId)));
    }

    @Override
    public List<AcademicFile> queryFiles(String userId, int limit) {
        return AcademicPOConverter.toFiles(academicAgentDao.queryFiles(userId, limit));
    }

    @Override
    public List<AcademicFile> queryFilesBySession(String userId, String sessionId) {
        return AcademicPOConverter.toFiles(academicAgentDao.queryFilesBySession(userId, sessionId));
    }

    @Override
    public void deleteFile(String userId, String fileId) {
        academicAgentDao.deleteFile(userId, fileId);
    }

    @Override
    public void saveArtifact(AcademicArtifact artifact) {
        academicAgentDao.insertArtifact(AcademicPOConverter.toPO(artifact));
    }

    @Override
    public List<AcademicArtifact> queryArtifacts(String userId, String sessionId) {
        return AcademicPOConverter.toArtifacts(academicAgentDao.queryArtifacts(userId, sessionId));
    }
}
