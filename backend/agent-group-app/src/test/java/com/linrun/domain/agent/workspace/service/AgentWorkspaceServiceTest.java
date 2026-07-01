package com.linrun.domain.agent.workspace.service;

import com.linrun.api.dto.AgentWorkspaceCreateRequest;
import com.linrun.api.dto.AgentWorkspaceFileBindRequest;
import com.linrun.api.dto.AgentWorkspacePatchCreateRequest;
import com.linrun.api.dto.AgentWorkspaceResponse;
import com.linrun.domain.agent.workspace.adapter.AgentWorkspaceRepository;
import com.linrun.domain.agent.workspace.model.AgentWorkspace;
import com.linrun.domain.agent.workspace.model.AgentWorkspaceFile;
import com.linrun.domain.agent.workspace.model.AgentWorkspacePatch;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorkspaceServiceTest {

    @Test
    void shouldCreateProjectBindFileAndApplyPatchAfterConfirmation() {
        AgentWorkspaceService service = new AgentWorkspaceService(new FakeProjectRepository());
        AgentWorkspaceCreateRequest createRequest = new AgentWorkspaceCreateRequest();
        createRequest.setTitle("Open-set AMR Paper");
        createRequest.setResearchQuestion("How to improve open-set recognition?");
        createRequest.setTargetVenue("TWC");

        AgentWorkspaceResponse created = service.createProject("U1001", createRequest);
        AgentWorkspaceFileBindRequest fileRequest = new AgentWorkspaceFileBindRequest();
        fileRequest.setFileId("FILE1001");
        fileRequest.setFileName("introduction.md");
        fileRequest.setFolderType("draftManuscripts");
        fileRequest.setContentPreview("old intro");

        AgentWorkspaceResponse withFile = service.bindFile("U1001", created.getProjectId(), fileRequest);

        AgentWorkspacePatchCreateRequest patchRequest = new AgentWorkspacePatchCreateRequest();
        patchRequest.setFileId("FILE1001");
        patchRequest.setTitle("Strengthen motivation");
        patchRequest.setReason("Need a clearer research gap.");
        patchRequest.setBeforeText("old intro");
        patchRequest.setAfterText("new intro");
        AgentWorkspaceResponse withPatch = service.proposePatch("U1001", created.getProjectId(), patchRequest);

        assertEquals(1, withFile.getFileCount());
        assertEquals(1, withPatch.getPendingPatchCount());
        assertEquals("PENDING", withPatch.getPatches().getFirst().getStatus());

        AgentWorkspaceResponse applied = service.applyPatch(
                "U1001",
                created.getProjectId(),
                withPatch.getPatches().getFirst().getPatchId());

        assertEquals(0, applied.getPendingPatchCount());
        assertEquals("APPLIED", applied.getPatches().getFirst().getStatus());
        assertEquals("new intro", applied.getFiles().getFirst().getContentPreview());
    }

    @Test
    void shouldRejectCrossUserProjectAccess() {
        AgentWorkspaceService service = new AgentWorkspaceService(new FakeProjectRepository());
        AgentWorkspaceCreateRequest createRequest = new AgentWorkspaceCreateRequest();
        createRequest.setTitle("Private Project");

        AgentWorkspaceResponse project = service.createProject("U1001", createRequest);

        assertThrows(AppException.class, () -> service.queryProject("U2002", project.getProjectId()));
    }

    private static final class FakeProjectRepository implements AgentWorkspaceRepository {

        private final Map<String, AgentWorkspace> projects = new LinkedHashMap<>();

        @Override
        public void saveProject(AgentWorkspace project) {
            projects.put(project.getProjectId(), copy(project));
        }

        @Override
        public List<AgentWorkspace> queryProjects(String userId, int limit) {
            return projects.values().stream()
                    .filter(project -> userId.equals(project.getUserId()))
                    .sorted(Comparator.comparing(AgentWorkspace::getUpdateTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .map(FakeProjectRepository::copy)
                    .toList();
        }

        @Override
        public Optional<AgentWorkspace> queryProject(String userId, String projectId) {
            AgentWorkspace project = projects.get(projectId);
            if (project == null || !userId.equals(project.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(copy(project));
        }

        @Override
        public void saveFile(String userId, String projectId, AgentWorkspaceFile file) {
            AgentWorkspace project = require(userId, projectId);
            project.getFiles().removeIf(item -> file.getFileId().equals(item.getFileId()));
            project.getFiles().add(copy(file));
        }

        @Override
        public void savePatch(String userId, String projectId, AgentWorkspacePatch patch) {
            require(userId, projectId).getPatches().add(copy(patch));
        }

        @Override
        public void applyPatch(String userId, String projectId, AgentWorkspacePatch patch) {
            AgentWorkspace project = require(userId, projectId);
            project.getPatches().stream()
                    .filter(item -> patch.getPatchId().equals(item.getPatchId()))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setStatus(patch.getStatus());
                        item.setApplyTime(patch.getApplyTime());
                    });
        }

        @Override
        public void updateFilePreview(String userId, String projectId, AgentWorkspaceFile file) {
            AgentWorkspace project = require(userId, projectId);
            project.getFiles().stream()
                    .filter(item -> file.getFileId().equals(item.getFileId()))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setSummary(file.getSummary());
                        item.setContentPreview(file.getContentPreview());
                    });
        }

        @Override
        public void touchProject(String userId, String projectId, LocalDateTime updateTime) {
            require(userId, projectId).setUpdateTime(updateTime);
        }

        private AgentWorkspace require(String userId, String projectId) {
            AgentWorkspace project = projects.get(projectId);
            if (project == null || !userId.equals(project.getUserId())) {
                throw new IllegalStateException("project not found");
            }
            return project;
        }

        private static AgentWorkspace copy(AgentWorkspace source) {
            AgentWorkspace target = new AgentWorkspace();
            target.setProjectId(source.getProjectId());
            target.setUserId(source.getUserId());
            target.setTitle(source.getTitle());
            target.setResearchQuestion(source.getResearchQuestion());
            target.setTargetVenue(source.getTargetVenue());
            target.setWritingStatus(source.getWritingStatus());
            target.setProgressNote(source.getProgressNote());
            target.setCreateTime(source.getCreateTime());
            target.setUpdateTime(source.getUpdateTime());
            source.getFiles().forEach(file -> target.getFiles().add(copy(file)));
            source.getPatches().forEach(patch -> target.getPatches().add(copy(patch)));
            return target;
        }

        private static AgentWorkspaceFile copy(AgentWorkspaceFile source) {
            AgentWorkspaceFile target = new AgentWorkspaceFile();
            target.setFileId(source.getFileId());
            target.setFileName(source.getFileName());
            target.setFileType(source.getFileType());
            target.setFolderType(source.getFolderType());
            target.setSummary(source.getSummary());
            target.setContentPreview(source.getContentPreview());
            target.setCreateTime(source.getCreateTime());
            return target;
        }

        private static AgentWorkspacePatch copy(AgentWorkspacePatch source) {
            AgentWorkspacePatch target = new AgentWorkspacePatch();
            target.setPatchId(source.getPatchId());
            target.setFileId(source.getFileId());
            target.setTitle(source.getTitle());
            target.setReason(source.getReason());
            target.setBeforeText(source.getBeforeText());
            target.setAfterText(source.getAfterText());
            target.setStatus(source.getStatus());
            target.setCreateTime(source.getCreateTime());
            target.setApplyTime(source.getApplyTime());
            return target;
        }
    }
}















