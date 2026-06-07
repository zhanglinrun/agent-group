package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.academic.project.adapter.AcademicProjectRepository;
import com.linrun.domain.academic.project.model.AcademicProject;
import com.linrun.domain.academic.project.model.AcademicProjectFile;
import com.linrun.domain.academic.project.model.AcademicProjectPatch;
import com.linrun.infrastructure.converter.AcademicProjectPOConverter;
import com.linrun.infrastructure.dao.IAcademicProjectDao;
import com.linrun.infrastructure.po.AcademicProjectPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAcademicProjectRepository implements AcademicProjectRepository {

    private final IAcademicProjectDao academicProjectDao;

    public MyBatisAcademicProjectRepository(IAcademicProjectDao academicProjectDao) {
        this.academicProjectDao = academicProjectDao;
    }

    @Override
    public void saveProject(AcademicProject project) {
        academicProjectDao.insertProject(AcademicProjectPOConverter.toPO(project));
    }

    @Override
    public List<AcademicProject> queryProjects(String userId, int limit) {
        return AcademicProjectPOConverter.toProjects(academicProjectDao.queryProjects(userId, limit)).stream()
                .map(project -> fillChildren(userId, project))
                .toList();
    }

    @Override
    public Optional<AcademicProject> queryProject(String userId, String projectId) {
        AcademicProjectPO project = academicProjectDao.queryProject(userId, projectId);
        return Optional.ofNullable(fillChildren(userId, AcademicProjectPOConverter.toEntity(project)));
    }

    @Override
    public void saveFile(String userId, String projectId, AcademicProjectFile file) {
        academicProjectDao.upsertFile(AcademicProjectPOConverter.toPO(userId, projectId, file));
    }

    @Override
    public void savePatch(String userId, String projectId, AcademicProjectPatch patch) {
        academicProjectDao.insertPatch(AcademicProjectPOConverter.toPO(userId, projectId, patch));
    }

    @Override
    public void applyPatch(String userId, String projectId, AcademicProjectPatch patch) {
        academicProjectDao.updatePatchApply(AcademicProjectPOConverter.toPO(userId, projectId, patch));
    }

    @Override
    public void updateFilePreview(String userId, String projectId, AcademicProjectFile file) {
        academicProjectDao.updateFilePreview(AcademicProjectPOConverter.toPO(userId, projectId, file));
    }

    @Override
    public void touchProject(String userId, String projectId, LocalDateTime updateTime) {
        academicProjectDao.touchProject(userId, projectId, updateTime);
    }

    private AcademicProject fillChildren(String userId, AcademicProject project) {
        if (project == null) {
            return null;
        }
        project.getFiles().clear();
        project.getFiles().addAll(AcademicProjectPOConverter.toFiles(
                academicProjectDao.queryFiles(userId, project.getProjectId())));
        project.getPatches().clear();
        project.getPatches().addAll(AcademicProjectPOConverter.toPatches(
                academicProjectDao.queryPatches(userId, project.getProjectId())));
        return project;
    }
}
