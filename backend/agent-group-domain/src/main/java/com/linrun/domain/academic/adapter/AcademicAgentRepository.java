package com.linrun.domain.academic.adapter;

import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;

import java.util.List;
import java.util.Optional;

public interface AcademicAgentRepository {

    void saveSessionIfAbsent(AcademicSession session);

    void updateSession(AcademicSession session);

    void saveMessage(AcademicMessage message);

    List<AcademicMessage> queryMessages(String userId, String sessionId);

    Optional<AcademicSession> querySession(String userId, String sessionId);

    List<AcademicSession> querySessions(String userId, int limit);

    void deleteSession(String userId, String sessionId);

    void saveFile(AcademicFile file);

    Optional<AcademicFile> queryFile(String userId, String fileId);

    List<AcademicFile> queryFiles(String userId, int limit);

    List<AcademicFile> queryFilesBySession(String userId, String sessionId);

    void deleteFile(String userId, String fileId);

    void saveArtifact(AcademicArtifact artifact);

    List<AcademicArtifact> queryArtifacts(String userId, String sessionId);
}
