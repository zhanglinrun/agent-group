package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.workspace.adapter.AgentWorkspaceRepository;
import com.linrun.domain.agent.workspace.model.AgentWorkspace;
import com.linrun.domain.agent.workspace.model.AgentWorkspaceFile;
import com.linrun.domain.agent.workspace.model.AgentWorkspacePatch;
import com.linrun.infrastructure.agent.converter.AgentWorkspacePOConverter;
import com.linrun.infrastructure.dao.IAgentWorkspaceDao;
import com.linrun.infrastructure.po.AgentWorkspacePO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAgentWorkspaceRepository implements AgentWorkspaceRepository {

    private final IAgentWorkspaceDao agentWorkspaceDao;

    public MyBatisAgentWorkspaceRepository(IAgentWorkspaceDao agentWorkspaceDao) {
        this.agentWorkspaceDao = agentWorkspaceDao;
    }

    @Override
    public void saveProject(AgentWorkspace project) {
        agentWorkspaceDao.insertProject(AgentWorkspacePOConverter.toPO(project));
    }

    @Override
    public List<AgentWorkspace> queryProjects(String userId, int limit) {
        return AgentWorkspacePOConverter.toProjects(agentWorkspaceDao.queryProjects(userId, limit)).stream()
                .map(project -> fillChildren(userId, project))
                .toList();
    }

    @Override
    public Optional<AgentWorkspace> queryProject(String userId, String projectId) {
        AgentWorkspacePO project = agentWorkspaceDao.queryProject(userId, projectId);
        return Optional.ofNullable(fillChildren(userId, AgentWorkspacePOConverter.toEntity(project)));
    }

    @Override
    public void saveFile(String userId, String projectId, AgentWorkspaceFile file) {
        agentWorkspaceDao.upsertFile(AgentWorkspacePOConverter.toPO(userId, projectId, file));
    }

    @Override
    public void savePatch(String userId, String projectId, AgentWorkspacePatch patch) {
        agentWorkspaceDao.insertPatch(AgentWorkspacePOConverter.toPO(userId, projectId, patch));
    }

    @Override
    public void applyPatch(String userId, String projectId, AgentWorkspacePatch patch) {
        agentWorkspaceDao.updatePatchApply(AgentWorkspacePOConverter.toPO(userId, projectId, patch));
    }

    @Override
    public void updateFilePreview(String userId, String projectId, AgentWorkspaceFile file) {
        agentWorkspaceDao.updateFilePreview(AgentWorkspacePOConverter.toPO(userId, projectId, file));
    }

    @Override
    public void touchProject(String userId, String projectId, LocalDateTime updateTime) {
        agentWorkspaceDao.touchProject(userId, projectId, updateTime);
    }

    private AgentWorkspace fillChildren(String userId, AgentWorkspace project) {
        if (project == null) {
            return null;
        }
        project.getFiles().clear();
        project.getFiles().addAll(AgentWorkspacePOConverter.toFiles(
                agentWorkspaceDao.queryFiles(userId, project.getProjectId())));
        project.getPatches().clear();
        project.getPatches().addAll(AgentWorkspacePOConverter.toPatches(
                agentWorkspaceDao.queryPatches(userId, project.getProjectId())));
        return project;
    }
}















