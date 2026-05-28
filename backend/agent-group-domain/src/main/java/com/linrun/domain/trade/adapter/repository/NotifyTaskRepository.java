package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.notify.NotifyTask;

import java.util.List;
import java.util.Optional;

public interface NotifyTaskRepository {

    void save(NotifyTask notifyTask);

    List<NotifyTask> queryUnExecutedNotifyTaskList(int limit);

    List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId);

    default List<NotifyTask> queryRecentNotifyTaskList(int limit) {
        return List.of();
    }

    Optional<NotifyTask> queryNotifyTaskByUuid(String uuid);

    int updateNotifyTaskStatusProcessing(NotifyTask notifyTask);

    int updateNotifyTaskStatusSuccess(NotifyTask notifyTask);

    int updateNotifyTaskStatusRetry(NotifyTask notifyTask);

    int updateNotifyTaskStatusError(NotifyTask notifyTask);
}
