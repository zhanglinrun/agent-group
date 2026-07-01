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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentWorkspaceService {

    private static final int DEFAULT_TEXT_LIMIT = 2000;

    private final AgentWorkspaceRepository projectRepository;

    public AgentWorkspaceService(AgentWorkspaceRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public AgentWorkspaceResponse createProject(String userId, AgentWorkspaceCreateRequest request) {
        validateUserId(userId);
        AgentWorkspaceCreateRequest safeRequest = request == null ? new AgentWorkspaceCreateRequest() : request;
        String title = safe(safeRequest.getTitle());
        if (!StringUtils.hasText(title)) {
            throw new AppException("AGENT_WORKSPACE_0001", "项目标题不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentWorkspace project = new AgentWorkspace();
        project.setProjectId(nextId("AP"));
        project.setUserId(userId);
        project.setTitle(limit(title, 120));
        project.setResearchQuestion(limit(safeRequest.getResearchQuestion(), 500));
        project.setTargetVenue(limit(safeRequest.getTargetVenue(), 120));
        project.setWritingStatus(StringUtils.hasText(safeRequest.getWritingStatus())
                ? limit(safeRequest.getWritingStatus(), 40)
                : "DRAFTING");
        project.setProgressNote(limit(safeRequest.getProgressNote(), 500));
        project.setCreateTime(now);
        project.setUpdateTime(now);
        projectRepository.saveProject(project);
        return toResponse(project);
    }

    public List<AgentWorkspaceResponse> queryProjects(String userId, int limit) {
        validateUserId(userId);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return projectRepository.queryProjects(userId, safeLimit).stream()
                .sorted(Comparator.comparing(AgentWorkspace::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .map(this::toResponse)
                .toList();
    }

    public AgentWorkspaceResponse queryProject(String userId, String projectId) {
        return toResponse(requireProject(userId, projectId));
    }

    public AgentWorkspaceResponse bindFile(String userId,
                                            String projectId,
                                            AgentWorkspaceFileBindRequest request) {
        AgentWorkspace project = requireProject(userId, projectId);
        AgentWorkspaceFileBindRequest safeRequest = request == null ? new AgentWorkspaceFileBindRequest() : request;
        String fileId = safe(safeRequest.getFileId());
        if (!StringUtils.hasText(fileId)) {
            throw new AppException("AGENT_WORKSPACE_0002", "文件编号不能为空");
        }
        project.getFiles().removeIf(file -> fileId.equals(file.getFileId()));
        AgentWorkspaceFile file = new AgentWorkspaceFile();
        file.setFileId(fileId);
        file.setFileName(limit(firstText(safeRequest.getFileName(), fileId), 160));
        file.setFileType(limit(safeRequest.getFileType(), 80));
        file.setFolderType(StringUtils.hasText(safeRequest.getFolderType())
                ? limit(safeRequest.getFolderType(), 40)
                : "draftManuscripts");
        file.setSummary(limit(safeRequest.getSummary(), 800));
        file.setContentPreview(limit(safeRequest.getContentPreview(), DEFAULT_TEXT_LIMIT));
        file.setCreateTime(LocalDateTime.now());
        project.getFiles().add(file);
        projectRepository.saveFile(userId, projectId, file);
        touch(userId, projectId, project);
        return queryProject(userId, projectId);
    }

    public AgentWorkspaceResponse proposePatch(String userId,
                                                String projectId,
                                                AgentWorkspacePatchCreateRequest request) {
        AgentWorkspace project = requireProject(userId, projectId);
        AgentWorkspacePatchCreateRequest safeRequest = request == null
                ? new AgentWorkspacePatchCreateRequest()
                : request;
        String fileId = safe(safeRequest.getFileId());
        if (!StringUtils.hasText(fileId)) {
            throw new AppException("AGENT_WORKSPACE_0003", "补丁必须关联文件");
        }
        boolean fileExists = project.getFiles().stream().anyMatch(file -> fileId.equals(file.getFileId()));
        if (!fileExists) {
            throw new AppException("AGENT_WORKSPACE_0004", "文件不属于当前项目");
        }
        AgentWorkspacePatch patch = new AgentWorkspacePatch();
        patch.setPatchId(nextId("PATCH"));
        patch.setFileId(fileId);
        patch.setTitle(limit(firstText(safeRequest.getTitle(), "内容修改建议"), 160));
        patch.setReason(limit(safeRequest.getReason(), 1000));
        patch.setBeforeText(limit(safeRequest.getBeforeText(), DEFAULT_TEXT_LIMIT));
        patch.setAfterText(limit(safeRequest.getAfterText(), DEFAULT_TEXT_LIMIT));
        patch.setStatus(AgentWorkspacePatch.STATUS_PENDING);
        patch.setCreateTime(LocalDateTime.now());
        project.getPatches().add(patch);
        projectRepository.savePatch(userId, projectId, patch);
        touch(userId, projectId, project);
        return queryProject(userId, projectId);
    }

    public AgentWorkspaceResponse applyPatch(String userId, String projectId, String patchId) {
        AgentWorkspace project = requireProject(userId, projectId);
        AgentWorkspacePatch patch = project.getPatches().stream()
                .filter(item -> safe(patchId).equals(item.getPatchId()))
                .findFirst()
                .orElseThrow(() -> new AppException("AGENT_WORKSPACE_0005", "补丁不存在或无权访问"));
        if (!AgentWorkspacePatch.STATUS_APPLIED.equals(patch.getStatus())) {
            patch.setStatus(AgentWorkspacePatch.STATUS_APPLIED);
            patch.setApplyTime(LocalDateTime.now());
            projectRepository.applyPatch(userId, projectId, patch);
            project.getFiles().stream()
                    .filter(file -> patch.getFileId().equals(file.getFileId()))
                    .findFirst()
                    .ifPresent(file -> {
                        file.setContentPreview(limit(patch.getAfterText(), DEFAULT_TEXT_LIMIT));
                        file.setSummary(limit(firstText(patch.getReason(), file.getSummary()), 800));
                        projectRepository.updateFilePreview(userId, projectId, file);
                    });
        }
        touch(userId, projectId, project);
        return queryProject(userId, projectId);
    }

    public Map<String, Object> projectContext(String userId, String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return Map.of();
        }
        AgentWorkspace project = requireProject(userId, projectId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectId", project.getProjectId());
        context.put("title", project.getTitle());
        context.put("researchQuestion", project.getResearchQuestion());
        context.put("targetVenue", project.getTargetVenue());
        context.put("writingStatus", project.getWritingStatus());
        context.put("progressNote", project.getProgressNote());
        context.put("fileCount", project.getFiles().size());
        context.put("pendingPatchCount", pendingPatchCount(project));
        context.put("files", project.getFiles().stream().map(this::fileMap).toList());
        return context;
    }

    private AgentWorkspace requireProject(String userId, String projectId) {
        validateUserId(userId);
        String safeProjectId = safe(projectId);
        return projectRepository.queryProject(userId, safeProjectId)
                .orElseThrow(() -> new AppException("AGENT_WORKSPACE_0006", "任务项目不存在或无权访问"));
    }

    private AgentWorkspaceResponse toResponse(AgentWorkspace project) {
        AgentWorkspaceResponse response = new AgentWorkspaceResponse();
        response.setProjectId(project.getProjectId());
        response.setTitle(project.getTitle());
        response.setResearchQuestion(project.getResearchQuestion());
        response.setTargetVenue(project.getTargetVenue());
        response.setWritingStatus(project.getWritingStatus());
        response.setProgressNote(project.getProgressNote());
        response.setFileCount(project.getFiles().size());
        response.setPendingPatchCount(pendingPatchCount(project));
        response.setCreateTime(project.getCreateTime());
        response.setUpdateTime(project.getUpdateTime());
        response.setFiles(project.getFiles().stream().map(this::file).toList());
        response.setPatches(project.getPatches().stream().map(this::patch).toList());
        return response;
    }

    private AgentWorkspaceResponse.ProjectFile file(AgentWorkspaceFile file) {
        AgentWorkspaceResponse.ProjectFile dto = new AgentWorkspaceResponse.ProjectFile();
        dto.setFileId(file.getFileId());
        dto.setFileName(file.getFileName());
        dto.setFileType(file.getFileType());
        dto.setFolderType(file.getFolderType());
        dto.setSummary(file.getSummary());
        dto.setContentPreview(file.getContentPreview());
        dto.setCreateTime(file.getCreateTime());
        return dto;
    }

    private AgentWorkspaceResponse.ProjectPatch patch(AgentWorkspacePatch patch) {
        AgentWorkspaceResponse.ProjectPatch dto = new AgentWorkspaceResponse.ProjectPatch();
        dto.setPatchId(patch.getPatchId());
        dto.setFileId(patch.getFileId());
        dto.setTitle(patch.getTitle());
        dto.setReason(patch.getReason());
        dto.setBeforeText(patch.getBeforeText());
        dto.setAfterText(patch.getAfterText());
        dto.setStatus(patch.getStatus());
        dto.setCreateTime(patch.getCreateTime());
        dto.setApplyTime(patch.getApplyTime());
        return dto;
    }

    private Map<String, Object> fileMap(AgentWorkspaceFile file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileId", file.getFileId());
        data.put("fileName", file.getFileName());
        data.put("folderType", file.getFolderType());
        data.put("summary", file.getSummary());
        data.put("contentPreview", file.getContentPreview());
        return data;
    }

    private int pendingPatchCount(AgentWorkspace project) {
        return (int) project.getPatches().stream()
                .filter(patch -> AgentWorkspacePatch.STATUS_PENDING.equals(patch.getStatus()))
                .count();
    }

    private void touch(String userId, String projectId, AgentWorkspace project) {
        LocalDateTime now = LocalDateTime.now();
        project.setUpdateTime(now);
        projectRepository.touchProject(userId, projectId, now);
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new AppException("AUTH_0001", "用户未登录");
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        String text = safe(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength));
    }

    private String nextId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}















