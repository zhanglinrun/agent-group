package com.linrun.trigger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.notify.response.NotifyTaskExecuteResponse;
import com.linrun.domain.dcc.adapter.DynamicConfigRepository;
import com.linrun.domain.dcc.model.DynamicConfig;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.notify.adapter.NotifyTaskRepository;
import com.linrun.domain.notify.model.NotifyTask;
import com.linrun.domain.trade.adapter.TradeEventPublisher;
import com.linrun.domain.trade.model.TradeEventMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyTaskServiceTest {

    @Test
    void shouldCreateAndExecuteBlankHttpNotifyTask() {
        FakeNotifyTaskRepository notifyTaskRepository = new FakeNotifyTaskRepository();
        NotifyTaskService service = new NotifyTaskService(
                notifyTaskRepository,
                new DynamicConfigService(new FakeDynamicConfigRepository()),
                TradeEventPublisher.noop(),
                new ObjectMapper());
        GroupBuyTeam team = new GroupBuyTeam();
        team.setTeamId("T10001");
        team.setActivityId("A10001");

        service.createGroupSettlementTask(team, List.of("O10001", "O10002"));
        NotifyTaskExecuteResponse response = service.execNotifyJob("T10001");

        assertEquals(1, response.getWaitCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(NotifyTask.STATUS_SUCCESS, notifyTaskRepository.tasks.get(0).getNotifyStatus());
        assertTrue(notifyTaskRepository.tasks.get(0).getParameterJson().contains("O10001"));
    }

    private static class FakeNotifyTaskRepository implements NotifyTaskRepository {

        private final List<NotifyTask> tasks = new ArrayList<>();

        @Override
        public void save(NotifyTask notifyTask) {
            tasks.add(notifyTask);
        }

        @Override
        public List<NotifyTask> queryUnExecutedNotifyTaskList(int limit) {
            return tasks.stream()
                    .filter(task -> task.getNotifyStatus() == NotifyTask.STATUS_INIT
                            || task.getNotifyStatus() == NotifyTask.STATUS_RETRY)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<NotifyTask> queryUnExecutedNotifyTaskList(String teamId) {
            return queryUnExecutedNotifyTaskList(50).stream()
                    .filter(task -> teamId.equals(task.getTeamId()))
                    .toList();
        }

        @Override
        public int updateNotifyTaskStatusSuccess(NotifyTask notifyTask) {
            notifyTask.setNotifyStatus(NotifyTask.STATUS_SUCCESS);
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusRetry(NotifyTask notifyTask) {
            notifyTask.setNotifyStatus(NotifyTask.STATUS_RETRY);
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusError(NotifyTask notifyTask) {
            notifyTask.setNotifyStatus(NotifyTask.STATUS_ERROR);
            return 1;
        }
    }

    private static class FakeDynamicConfigRepository implements DynamicConfigRepository {

        private final Map<String, DynamicConfig> configs = new HashMap<>();

        @Override
        public Optional<DynamicConfig> queryByKey(String configKey) {
            return Optional.ofNullable(configs.get(configKey));
        }

        @Override
        public void saveOrUpdate(DynamicConfig config) {
            configs.put(config.getConfigKey(), config);
        }
    }
}
