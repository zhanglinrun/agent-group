package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.NotifyTaskRepository;
import com.linrun.domain.trade.model.notify.NotifyTask;
import com.linrun.infrastructure.trade.converter.TradePOConverter;
import com.linrun.infrastructure.dao.INotifyTaskDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisNotifyTaskRepository implements NotifyTaskRepository {

    private final INotifyTaskDao notifyTaskDao;

    public MyBatisNotifyTaskRepository(INotifyTaskDao notifyTaskDao) {
        this.notifyTaskDao = notifyTaskDao;
    }

    @Override
    public void save(NotifyTask notifyTask) {
        notifyTaskDao.insert(TradePOConverter.toPO(notifyTask));
    }

    @Override
    public List<NotifyTask> queryUnExecutedNotifyTaskList(int limit) {
        return TradePOConverter.toNotifyTasks(notifyTaskDao.queryUnExecutedNotifyTaskList(limit));
    }

    @Override
    public List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId) {
        return TradePOConverter.toNotifyTasks(notifyTaskDao.queryUnExecutedNotifyTaskListByTeamId(teamId));
    }

    @Override
    public List<NotifyTask> queryRecentNotifyTaskList(int limit) {
        return TradePOConverter.toNotifyTasks(notifyTaskDao.queryRecentNotifyTaskList(Math.max(1, limit)));
    }

    @Override
    public Optional<NotifyTask> queryNotifyTaskByUuid(String uuid) {
        return Optional.ofNullable(TradePOConverter.toEntity(notifyTaskDao.queryNotifyTaskByUuid(uuid)));
    }

    @Override
    public int updateNotifyTaskStatusProcessing(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusProcessing(TradePOConverter.toPO(notifyTask));
    }

    @Override
    public int updateNotifyTaskStatusSuccess(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(TradePOConverter.toPO(notifyTask));
    }

    @Override
    public int updateNotifyTaskStatusRetry(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(TradePOConverter.toPO(notifyTask));
    }

    @Override
    public int updateNotifyTaskStatusError(NotifyTask notifyTask) {
        return notifyTaskDao.updateNotifyTaskStatusError(TradePOConverter.toPO(notifyTask));
    }
}















