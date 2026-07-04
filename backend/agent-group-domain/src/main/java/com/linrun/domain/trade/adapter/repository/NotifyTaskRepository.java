package com.linrun.domain.trade.adapter.repository;

import com.linrun.domain.trade.model.notify.NotifyTask;

import java.util.List;
import java.util.Optional;

public interface NotifyTaskRepository {

    void save(NotifyTask notifyTask);

    /**
     * 插入通知任务；uuid 已存在时视为幂等成功并返回 false。
     */
    default boolean saveIfAbsent(NotifyTask notifyTask) {
        if (notifyTask != null
                && notifyTask.getUuid() != null
                && queryNotifyTaskByUuid(notifyTask.getUuid()).isPresent()) {
            return false;
        }
        save(notifyTask);
        return true;
    }

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















