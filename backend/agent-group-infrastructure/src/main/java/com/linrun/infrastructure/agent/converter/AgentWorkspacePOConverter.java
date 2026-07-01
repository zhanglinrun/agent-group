package com.linrun.infrastructure.agent.converter;

import com.linrun.domain.agent.workspace.model.AgentWorkspace;
import com.linrun.domain.agent.workspace.model.AgentWorkspaceFile;
import com.linrun.domain.agent.workspace.model.AgentWorkspacePatch;
import com.linrun.infrastructure.po.AgentWorkspaceFilePO;
import com.linrun.infrastructure.po.AgentWorkspacePO;
import com.linrun.infrastructure.po.AgentWorkspacePatchPO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;

public final class AgentWorkspacePOConverter {

    private AgentWorkspacePOConverter() {
    }

    public static AgentWorkspacePO toPO(AgentWorkspace entity) {
        if (entity == null) {
            return null;
        }
        AgentWorkspacePO po = new AgentWorkspacePO();
        BeanUtils.copyProperties(entity, po);
        po.setProjectId(blank(po.getProjectId()));
        po.setUserId(blank(po.getUserId()));
        po.setTitle(blank(po.getTitle()));
        po.setResearchQuestion(blank(po.getResearchQuestion()));
        po.setTargetVenue(blank(po.getTargetVenue()));
        po.setWritingStatus(text(po.getWritingStatus(), "DRAFTING"));
        po.setProgressNote(blank(po.getProgressNote()));
        po.setCreateTime(time(po.getCreateTime()));
        po.setUpdateTime(time(po.getUpdateTime()));
        return po;
    }

    public static AgentWorkspace toEntity(AgentWorkspacePO po) {
        if (po == null) {
            return null;
        }
        AgentWorkspace entity = new AgentWorkspace();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentWorkspace> toProjects(List<AgentWorkspacePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentWorkspacePOConverter::toEntity).toList();
    }

    public static AgentWorkspaceFilePO toPO(String userId, String projectId, AgentWorkspaceFile entity) {
        if (entity == null) {
            return null;
        }
        AgentWorkspaceFilePO po = new AgentWorkspaceFilePO();
        BeanUtils.copyProperties(entity, po);
        po.setProjectId(blank(projectId));
        po.setUserId(blank(userId));
        po.setFileId(blank(po.getFileId()));
        po.setFileName(blank(po.getFileName()));
        po.setFileType(blank(po.getFileType()));
        po.setFolderType(text(po.getFolderType(), "draftManuscripts"));
        po.setSummary(blank(po.getSummary()));
        po.setContentPreview(blank(po.getContentPreview()));
        po.setCreateTime(time(po.getCreateTime()));
        return po;
    }

    public static AgentWorkspaceFile toEntity(AgentWorkspaceFilePO po) {
        if (po == null) {
            return null;
        }
        AgentWorkspaceFile entity = new AgentWorkspaceFile();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentWorkspaceFile> toFiles(List<AgentWorkspaceFilePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentWorkspacePOConverter::toEntity).toList();
    }

    public static AgentWorkspacePatchPO toPO(String userId, String projectId, AgentWorkspacePatch entity) {
        if (entity == null) {
            return null;
        }
        AgentWorkspacePatchPO po = new AgentWorkspacePatchPO();
        BeanUtils.copyProperties(entity, po);
        po.setProjectId(blank(projectId));
        po.setUserId(blank(userId));
        po.setPatchId(blank(po.getPatchId()));
        po.setFileId(blank(po.getFileId()));
        po.setTitle(blank(po.getTitle()));
        po.setReason(blank(po.getReason()));
        po.setBeforeText(blank(po.getBeforeText()));
        po.setAfterText(blank(po.getAfterText()));
        po.setStatus(text(po.getStatus(), AgentWorkspacePatch.STATUS_PENDING));
        po.setCreateTime(time(po.getCreateTime()));
        return po;
    }

    public static AgentWorkspacePatch toEntity(AgentWorkspacePatchPO po) {
        if (po == null) {
            return null;
        }
        AgentWorkspacePatch entity = new AgentWorkspacePatch();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentWorkspacePatch> toPatches(List<AgentWorkspacePatchPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentWorkspacePOConverter::toEntity).toList();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LocalDateTime time(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}















