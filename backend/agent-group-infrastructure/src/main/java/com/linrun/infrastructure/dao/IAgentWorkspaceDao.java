package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.AgentWorkspaceFilePO;
import com.linrun.infrastructure.po.AgentWorkspacePO;
import com.linrun.infrastructure.po.AgentWorkspacePatchPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAgentWorkspaceDao {

    void insertProject(AgentWorkspacePO project);

    List<AgentWorkspacePO> queryProjects(@Param("userId") String userId, @Param("limit") int limit);

    AgentWorkspacePO queryProject(@Param("userId") String userId, @Param("projectId") String projectId);

    void upsertFile(AgentWorkspaceFilePO file);

    List<AgentWorkspaceFilePO> queryFiles(@Param("userId") String userId, @Param("projectId") String projectId);

    void insertPatch(AgentWorkspacePatchPO patch);

    int updatePatchApply(AgentWorkspacePatchPO patch);

    List<AgentWorkspacePatchPO> queryPatches(@Param("userId") String userId, @Param("projectId") String projectId);

    int updateFilePreview(AgentWorkspaceFilePO file);

    int touchProject(@Param("userId") String userId,
                     @Param("projectId") String projectId,
                     @Param("updateTime") LocalDateTime updateTime);
}















