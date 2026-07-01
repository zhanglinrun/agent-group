package com.linrun.domain.agent.workspace.adapter;

import com.linrun.domain.agent.workspace.model.AgentWorkspace;
import com.linrun.domain.agent.workspace.model.AgentWorkspaceFile;
import com.linrun.domain.agent.workspace.model.AgentWorkspacePatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentWorkspaceRepository {

    void saveProject(AgentWorkspace project);

    List<AgentWorkspace> queryProjects(String userId, int limit);

    Optional<AgentWorkspace> queryProject(String userId, String projectId);

    void saveFile(String userId, String projectId, AgentWorkspaceFile file);

    void savePatch(String userId, String projectId, AgentWorkspacePatch patch);

    void applyPatch(String userId, String projectId, AgentWorkspacePatch patch);

    void updateFilePreview(String userId, String projectId, AgentWorkspaceFile file);

    void touchProject(String userId, String projectId, LocalDateTime updateTime);
}















