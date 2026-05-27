package com.linrun.domain.notify.adapter;

import com.linrun.domain.notify.model.NotifyTask;

import java.util.List;
import java.util.Optional;

public interface NotifyTaskRepository {

    void save(NotifyTask notifyTask);

    List<NotifyTask> queryUnExecutedNotifyTaskList(int limit);

    List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId);

    Optional<NotifyTask> queryNotifyTaskByUuid(String uuid);

    int updateNotifyTaskStatusProcessing(NotifyTask notifyTask);

    int updateNotifyTaskStatusSuccess(NotifyTask notifyTask);

    int updateNotifyTaskStatusRetry(NotifyTask notifyTask);

    int updateNotifyTaskStatusError(NotifyTask notifyTask);
}
