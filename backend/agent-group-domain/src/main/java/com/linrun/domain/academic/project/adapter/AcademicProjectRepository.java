package com.linrun.domain.academic.project.adapter;

import com.linrun.domain.academic.project.model.AcademicProject;
import com.linrun.domain.academic.project.model.AcademicProjectFile;
import com.linrun.domain.academic.project.model.AcademicProjectPatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AcademicProjectRepository {

    void saveProject(AcademicProject project);

    List<AcademicProject> queryProjects(String userId, int limit);

    Optional<AcademicProject> queryProject(String userId, String projectId);

    void saveFile(String userId, String projectId, AcademicProjectFile file);

    void savePatch(String userId, String projectId, AcademicProjectPatch patch);

    void applyPatch(String userId, String projectId, AcademicProjectPatch patch);

    void updateFilePreview(String userId, String projectId, AcademicProjectFile file);

    void touchProject(String userId, String projectId, LocalDateTime updateTime);
}
