package com.linrun.infrastructure.notify.repository;

import com.linrun.domain.notify.adapter.NotifyTaskRepository;
import com.linrun.domain.notify.model.NotifyTask;
import com.linrun.infrastructure.dao.INotifyTaskDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisNotifyTaskRepository implements NotifyTaskRepository {

    private final INotifyTaskDao notifyTaskDao;

    public MyBatisNotifyTaskRepository(INotifyTaskDao notifyTaskDao) {
        this.notifyTaskDao = notifyTaskDao;
    }

    @Override
    public void save(NotifyTask notifyTask) {
        notifyTaskDao.insert(notifyTask);
    }

    @Override
    public List<NotifyTask> queryUnExecutedNotifyTaskList(int limit) {
        return notifyTaskDao.queryUnExecutedNotifyTaskList(limit);
    }

    @Override
    public List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId) {
        return notifyTaskDao.queryUnExecutedNotifyTaskListByTeamId(teamId);
    }

    @Override
    public int updateNotifyTaskStatusSuccess(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(notifyTask);
    }

    @Override
    public int updateNotifyTaskStatusRetry(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(notifyTask);
    }

    @Override
    public int updateNotifyTaskStatusError(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusError(notifyTask);
    }
}
