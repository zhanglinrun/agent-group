package com.linrun.domain.agent.adapter;

import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.model.AgentFile;
import com.linrun.domain.agent.model.AgentMessage;
import com.linrun.domain.agent.model.AgentSession;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {

    void saveSessionIfAbsent(AgentSession session);

    void updateSession(AgentSession session);

    void saveMessage(AgentMessage message);

    List<AgentMessage> queryMessages(String userId, String sessionId);

    Optional<AgentSession> querySession(String userId, String sessionId);

    List<AgentSession> querySessions(String userId, int limit);

    void deleteSession(String userId, String sessionId);

    void saveFile(AgentFile file);

    Optional<AgentFile> queryFile(String userId, String fileId);

    List<AgentFile> queryFiles(String userId, int limit);

    List<AgentFile> queryFilesBySession(String userId, String sessionId);

    void deleteFile(String userId, String fileId);

    void saveArtifact(AgentArtifact artifact);

    List<AgentArtifact> queryArtifacts(String userId, String sessionId);
}















