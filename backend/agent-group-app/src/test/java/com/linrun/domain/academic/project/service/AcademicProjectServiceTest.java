package com.linrun.domain.academic.project.service;

import com.linrun.api.dto.AcademicProjectCreateRequest;
import com.linrun.api.dto.AcademicProjectFileBindRequest;
import com.linrun.api.dto.AcademicProjectPatchCreateRequest;
import com.linrun.api.dto.AcademicProjectResponse;
import com.linrun.domain.academic.project.adapter.AcademicProjectRepository;
import com.linrun.domain.academic.project.model.AcademicProject;
import com.linrun.domain.academic.project.model.AcademicProjectFile;
import com.linrun.domain.academic.project.model.AcademicProjectPatch;
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

class AcademicProjectServiceTest {

    @Test
    void shouldCreateProjectBindFileAndApplyPatchAfterConfirmation() {
        AcademicProjectService service = new AcademicProjectService(new FakeProjectRepository());
        AcademicProjectCreateRequest createRequest = new AcademicProjectCreateRequest();
        createRequest.setTitle("Open-set AMR Paper");
        createRequest.setResearchQuestion("How to improve open-set recognition?");
        createRequest.setTargetVenue("TWC");

        AcademicProjectResponse created = service.createProject("U1001", createRequest);
        AcademicProjectFileBindRequest fileRequest = new AcademicProjectFileBindRequest();
        fileRequest.setFileId("FILE1001");
        fileRequest.setFileName("introduction.md");
        fileRequest.setFolderType("draftManuscripts");
        fileRequest.setContentPreview("old intro");

        AcademicProjectResponse withFile = service.bindFile("U1001", created.getProjectId(), fileRequest);

        AcademicProjectPatchCreateRequest patchRequest = new AcademicProjectPatchCreateRequest();
        patchRequest.setFileId("FILE1001");
        patchRequest.setTitle("Strengthen motivation");
        patchRequest.setReason("Need a clearer research gap.");
        patchRequest.setBeforeText("old intro");
        patchRequest.setAfterText("new intro");
        AcademicProjectResponse withPatch = service.proposePatch("U1001", created.getProjectId(), patchRequest);

        assertEquals(1, withFile.getFileCount());
        assertEquals(1, withPatch.getPendingPatchCount());
        assertEquals("PENDING", withPatch.getPatches().getFirst().getStatus());

        AcademicProjectResponse applied = service.applyPatch(
                "U1001",
                created.getProjectId(),
                withPatch.getPatches().getFirst().getPatchId());

        assertEquals(0, applied.getPendingPatchCount());
        assertEquals("APPLIED", applied.getPatches().getFirst().getStatus());
        assertEquals("new intro", applied.getFiles().getFirst().getContentPreview());
    }

    @Test
    void shouldRejectCrossUserProjectAccess() {
        AcademicProjectService service = new AcademicProjectService(new FakeProjectRepository());
        AcademicProjectCreateRequest createRequest = new AcademicProjectCreateRequest();
        createRequest.setTitle("Private Project");

        AcademicProjectResponse project = service.createProject("U1001", createRequest);

        assertThrows(AppException.class, () -> service.queryProject("U2002", project.getProjectId()));
    }

    private static final class FakeProjectRepository implements AcademicProjectRepository {

        private final Map<String, AcademicProject> projects = new LinkedHashMap<>();

        @Override
        public void saveProject(AcademicProject project) {
            projects.put(project.getProjectId(), copy(project));
        }

        @Override
        public List<AcademicProject> queryProjects(String userId, int limit) {
            return projects.values().stream()
                    .filter(project -> userId.equals(project.getUserId()))
                    .sorted(Comparator.comparing(AcademicProject::getUpdateTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .map(FakeProjectRepository::copy)
                    .toList();
        }

        @Override
        public Optional<AcademicProject> queryProject(String userId, String projectId) {
            AcademicProject project = projects.get(projectId);
            if (project == null || !userId.equals(project.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(copy(project));
        }

        @Override
        public void saveFile(String userId, String projectId, AcademicProjectFile file) {
            AcademicProject project = require(userId, projectId);
            project.getFiles().removeIf(item -> file.getFileId().equals(item.getFileId()));
            project.getFiles().add(copy(file));
        }

        @Override
        public void savePatch(String userId, String projectId, AcademicProjectPatch patch) {
            require(userId, projectId).getPatches().add(copy(patch));
        }

        @Override
        public void applyPatch(String userId, String projectId, AcademicProjectPatch patch) {
            AcademicProject project = require(userId, projectId);
            project.getPatches().stream()
                    .filter(item -> patch.getPatchId().equals(item.getPatchId()))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setStatus(patch.getStatus());
                        item.setApplyTime(patch.getApplyTime());
                    });
        }

        @Override
        public void updateFilePreview(String userId, String projectId, AcademicProjectFile file) {
            AcademicProject project = require(userId, projectId);
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

        private AcademicProject require(String userId, String projectId) {
            AcademicProject project = projects.get(projectId);
            if (project == null || !userId.equals(project.getUserId())) {
                throw new IllegalStateException("project not found");
            }
            return project;
        }

        private static AcademicProject copy(AcademicProject source) {
            AcademicProject target = new AcademicProject();
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

        private static AcademicProjectFile copy(AcademicProjectFile source) {
            AcademicProjectFile target = new AcademicProjectFile();
            target.setFileId(source.getFileId());
            target.setFileName(source.getFileName());
            target.setFileType(source.getFileType());
            target.setFolderType(source.getFolderType());
            target.setSummary(source.getSummary());
            target.setContentPreview(source.getContentPreview());
            target.setCreateTime(source.getCreateTime());
            return target;
        }

        private static AcademicProjectPatch copy(AcademicProjectPatch source) {
            AcademicProjectPatch target = new AcademicProjectPatch();
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
