package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.NotifyTaskRepository;
import com.linrun.domain.trade.model.notify.NotifyTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发下 uuid 唯一插入：仅一笔成功，其余幂等返回 false，与 MyBatis saveIfAbsent 语义一致。
 */
class NotifyTaskSaveIfAbsentConcurrencyTest {

    @Test
    void saveIfAbsentAllowsOnlyOneWinnerUnderConcurrentInsert() throws InterruptedException {
        RacingNotifyTaskRepository repository = new RacingNotifyTaskRepository();
        int threads = 16;
        String uuid = "T90001_trade_settlement";
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    NotifyTask task = settlementTask(uuid);
                    if (repository.saveIfAbsent(task)) {
                        inserted.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, inserted.get(), "并发插入应只有一笔成功");
        assertEquals(threads - 1, skipped.get(), "其余应命中 uuid 幂等");
        assertEquals(1, repository.size());
    }

    private static NotifyTask settlementTask(String uuid) {
        NotifyTask task = new NotifyTask();
        task.setUuid(uuid);
        task.setTeamId("T90001");
        task.setActivityId("A90001");
        task.setNotifyCategory(NotifyTask.CATEGORY_TRADE_SETTLEMENT);
        task.setNotifyType(NotifyTask.TYPE_HTTP);
        task.setNotifyStatus(NotifyTask.STATUS_INIT);
        task.setNotifyCount(0);
        task.setParameterJson("{\"orderIds\":[\"O90001\"]}");
        return task;
    }

    private static final class RacingNotifyTaskRepository implements NotifyTaskRepository {

        private final ConcurrentHashMap<String, NotifyTask> tasks = new ConcurrentHashMap<>();

        @Override
        public void save(NotifyTask notifyTask) {
            if (notifyTask == null || notifyTask.getUuid() == null) {
                return;
            }
            tasks.putIfAbsent(notifyTask.getUuid(), notifyTask);
        }

        @Override
        public boolean saveIfAbsent(NotifyTask notifyTask) {
            if (notifyTask == null || notifyTask.getUuid() == null) {
                return false;
            }
            return tasks.putIfAbsent(notifyTask.getUuid(), notifyTask) == null;
        }

        @Override
        public List<NotifyTask> queryUnExecutedNotifyTaskList(int limit) {
            return tasks.values().stream().limit(limit).toList();
        }

        @Override
        public List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId) {
            return tasks.values().stream()
                    .filter(task -> teamId.equals(task.getTeamId()))
                    .toList();
        }

        @Override
        public Optional<NotifyTask> queryNotifyTaskByUuid(String uuid) {
            return Optional.ofNullable(tasks.get(uuid));
        }

        @Override
        public int updateNotifyTaskStatusProcessing(NotifyTask notifyTask) {
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusSuccess(NotifyTask notifyTask) {
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusRetry(NotifyTask notifyTask) {
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusError(NotifyTask notifyTask) {
            return 1;
        }

        int size() {
            return tasks.size();
        }
    }
}
