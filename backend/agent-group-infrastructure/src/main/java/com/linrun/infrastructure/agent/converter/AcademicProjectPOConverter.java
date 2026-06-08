package com.linrun.infrastructure.agent.converter;

import com.linrun.domain.academic.project.model.AcademicProject;
import com.linrun.domain.academic.project.model.AcademicProjectFile;
import com.linrun.domain.academic.project.model.AcademicProjectPatch;
import com.linrun.infrastructure.po.AcademicProjectFilePO;
import com.linrun.infrastructure.po.AcademicProjectPO;
import com.linrun.infrastructure.po.AcademicProjectPatchPO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;

public final class AcademicProjectPOConverter {

    private AcademicProjectPOConverter() {
    }

    public static AcademicProjectPO toPO(AcademicProject entity) {
        if (entity == null) {
            return null;
        }
        AcademicProjectPO po = new AcademicProjectPO();
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

    public static AcademicProject toEntity(AcademicProjectPO po) {
        if (po == null) {
            return null;
        }
        AcademicProject entity = new AcademicProject();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicProject> toProjects(List<AcademicProjectPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicProjectPOConverter::toEntity).toList();
    }

    public static AcademicProjectFilePO toPO(String userId, String projectId, AcademicProjectFile entity) {
        if (entity == null) {
            return null;
        }
        AcademicProjectFilePO po = new AcademicProjectFilePO();
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

    public static AcademicProjectFile toEntity(AcademicProjectFilePO po) {
        if (po == null) {
            return null;
        }
        AcademicProjectFile entity = new AcademicProjectFile();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicProjectFile> toFiles(List<AcademicProjectFilePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicProjectPOConverter::toEntity).toList();
    }

    public static AcademicProjectPatchPO toPO(String userId, String projectId, AcademicProjectPatch entity) {
        if (entity == null) {
            return null;
        }
        AcademicProjectPatchPO po = new AcademicProjectPatchPO();
        BeanUtils.copyProperties(entity, po);
        po.setProjectId(blank(projectId));
        po.setUserId(blank(userId));
        po.setPatchId(blank(po.getPatchId()));
        po.setFileId(blank(po.getFileId()));
        po.setTitle(blank(po.getTitle()));
        po.setReason(blank(po.getReason()));
        po.setBeforeText(blank(po.getBeforeText()));
        po.setAfterText(blank(po.getAfterText()));
        po.setStatus(text(po.getStatus(), AcademicProjectPatch.STATUS_PENDING));
        po.setCreateTime(time(po.getCreateTime()));
        return po;
    }

    public static AcademicProjectPatch toEntity(AcademicProjectPatchPO po) {
        if (po == null) {
            return null;
        }
        AcademicProjectPatch entity = new AcademicProjectPatch();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicProjectPatch> toPatches(List<AcademicProjectPatchPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicProjectPOConverter::toEntity).toList();
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
