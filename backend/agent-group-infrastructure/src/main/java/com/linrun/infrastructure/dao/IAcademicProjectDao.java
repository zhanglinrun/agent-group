package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.AcademicProjectFilePO;
import com.linrun.infrastructure.po.AcademicProjectPO;
import com.linrun.infrastructure.po.AcademicProjectPatchPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAcademicProjectDao {

    void insertProject(AcademicProjectPO project);

    List<AcademicProjectPO> queryProjects(@Param("userId") String userId, @Param("limit") int limit);

    AcademicProjectPO queryProject(@Param("userId") String userId, @Param("projectId") String projectId);

    void upsertFile(AcademicProjectFilePO file);

    List<AcademicProjectFilePO> queryFiles(@Param("userId") String userId, @Param("projectId") String projectId);

    void insertPatch(AcademicProjectPatchPO patch);

    int updatePatchApply(AcademicProjectPatchPO patch);

    List<AcademicProjectPatchPO> queryPatches(@Param("userId") String userId, @Param("projectId") String projectId);

    int updateFilePreview(AcademicProjectFilePO file);

    int touchProject(@Param("userId") String userId,
                     @Param("projectId") String projectId,
                     @Param("updateTime") LocalDateTime updateTime);
}
