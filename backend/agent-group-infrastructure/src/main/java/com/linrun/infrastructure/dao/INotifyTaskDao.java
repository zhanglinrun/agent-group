package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.NotifyTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface INotifyTaskDao {

    void insert(NotifyTaskPO notifyTask);

    List<NotifyTaskPO> queryUnExecutedNotifyTaskList(@Param("limit") int limit);

    List<NotifyTaskPO> queryUnExecutedNotifyTaskListByTeamId(@Param("teamId") String teamId);

    NotifyTaskPO queryNotifyTaskByUuid(@Param("uuid") String uuid);

    int updateNotifyTaskStatusProcessing(NotifyTaskPO notifyTask);

    int updateNotifyTaskStatusSuccess(NotifyTaskPO notifyTask);

    int updateNotifyTaskStatusRetry(NotifyTaskPO notifyTask);

    int updateNotifyTaskStatusError(NotifyTaskPO notifyTask);
}
