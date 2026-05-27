package com.linrun.infrastructure.dao;

import com.linrun.domain.notify.model.NotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface INotifyTaskDao {

    void insert(NotifyTask notifyTask);

    List<NotifyTask> queryUnExecutedNotifyTaskList(@Param("limit") int limit);

    List<NotifyTask> queryUnExecutedNotifyTaskListByTeamId(@Param("teamId") String teamId);

    NotifyTask queryNotifyTaskByUuid(@Param("uuid") String uuid);

    int updateNotifyTaskStatusProcessing(NotifyTask notifyTask);

    int updateNotifyTaskStatusSuccess(NotifyTask notifyTask);

    int updateNotifyTaskStatusRetry(NotifyTask notifyTask);

    int updateNotifyTaskStatusError(NotifyTask notifyTask);
}
