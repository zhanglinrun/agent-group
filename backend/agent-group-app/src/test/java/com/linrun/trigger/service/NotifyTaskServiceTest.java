package com.linrun.trigger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.api.notify.response.NotifyTaskExecuteResponse;
import com.linrun.domain.dcc.adapter.DynamicConfigRepository;
import com.linrun.domain.dcc.model.DynamicConfig;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.marketing.model.GroupBuyTeam;
import com.linrun.domain.notify.adapter.NotifyTaskRepository;
import com.linrun.domain.notify.model.NotifyTask;
import com.linrun.domain.order.adapter.TradeEventPublisher;
import com.linrun.domain.order.model.entity.TradeEventMessageEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    @Test
    void shouldPublishMqNotifyTaskWithConfiguredRoutingKey() {
        FakeNotifyTaskRepository notifyTaskRepository = new FakeNotifyTaskRepository();
        FakeTradeEventPublisher publisher = new FakeTradeEventPublisher();
        DynamicConfigService dynamicConfigService = new DynamicConfigService(new FakeDynamicConfigRepository());
        dynamicConfigService.updateConfig(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_TYPE, NotifyTask.TYPE_MQ);
        dynamicConfigService.updateConfig(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_MQ, "agent.group.notify.group-settlement");
        NotifyTaskService service = new NotifyTaskService(
                notifyTaskRepository,
                dynamicConfigService,
                publisher,
                new ObjectMapper());
        GroupBuyTeam team = new GroupBuyTeam();
        team.setTeamId("T10002");
        team.setActivityId("A10002");

        service.createGroupSettlementTask(team, List.of("O20001"));
        NotifyTaskExecuteResponse response = service.execNotifyJob("T10002");

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, publisher.messages.size());
        assertEquals("agent.group.notify.group-settlement", publisher.messages.get(0).getRoutingKey());
        assertEquals(NotifyTask.CATEGORY_TRADE_SETTLEMENT, publisher.messages.get(0).getEventType());
    }

    @Test
    void shouldMarkMqNotifyTaskErrorWhenRetryCountExceeded() {
        FakeNotifyTaskRepository notifyTaskRepository = new FakeNotifyTaskRepository();
        NotifyTask task = new NotifyTask();
        task.setActivityId("A10003");
        task.setTeamId("T10003");
        task.setNotifyCategory(NotifyTask.CATEGORY_TRADE_SETTLEMENT);
        task.setNotifyType(NotifyTask.TYPE_MQ);
        task.setNotifyMq("agent.group.notify.group-settlement");
        task.setNotifyCount(4);
        task.setNotifyStatus(NotifyTask.STATUS_RETRY);
        task.setParameterJson("{}");
        task.setUuid("T10003_trade_settlement");
        notifyTaskRepository.save(task);
        NotifyTaskService service = new NotifyTaskService(
                notifyTaskRepository,
                new DynamicConfigService(new FakeDynamicConfigRepository()),
                (TradeEventPublisher) message -> {
                    throw new IllegalStateException("mq unavailable");
                },
                new ObjectMapper());

        NotifyTaskExecuteResponse response = service.execNotifyJob("T10003");

        assertEquals(1, response.getErrorCount());
        assertEquals(NotifyTask.STATUS_ERROR, task.getNotifyStatus());
    }

    @Test
    void shouldCreateRefundNotifyTask() {
        FakeNotifyTaskRepository notifyTaskRepository = new FakeNotifyTaskRepository();
        DynamicConfigService dynamicConfigService = new DynamicConfigService(new FakeDynamicConfigRepository());
        dynamicConfigService.updateConfig(DynamicConfigService.GROUP_REFUND_NOTIFY_TYPE, NotifyTask.TYPE_MQ);
        NotifyTaskService service = new NotifyTaskService(
                notifyTaskRepository,
                dynamicConfigService,
                TradeEventPublisher.noop(),
                new ObjectMapper());
        GroupBuyCompensationResponse response = new GroupBuyCompensationResponse();
        response.setOrderId("O10001");
        response.setPayOrderId("P10001");
        response.setRefundId("R10001");
        response.setActivityId("A10001");
        response.setTeamId("T10001");
        response.setRefundAmount(new BigDecimal("2099.00"));

        service.createGroupRefundTask(response);

        assertEquals(1, notifyTaskRepository.tasks.size());
        assertEquals(NotifyTask.CATEGORY_TRADE_REFUND, notifyTaskRepository.tasks.get(0).getNotifyCategory());
        assertEquals("agent.group.notify.group-refund", notifyTaskRepository.tasks.get(0).getNotifyMq());
        assertTrue(notifyTaskRepository.tasks.get(0).getParameterJson().contains("R10001"));
    }

    @Test
    void shouldRetrySpecifiedErrorNotifyTaskByUuid() {
        FakeNotifyTaskRepository notifyTaskRepository = new FakeNotifyTaskRepository();
        NotifyTask task = new NotifyTask();
        task.setActivityId("A10004");
        task.setTeamId("T10004");
        task.setNotifyCategory(NotifyTask.CATEGORY_TRADE_REFUND);
        task.setNotifyType(NotifyTask.TYPE_HTTP);
        task.setNotifyCount(1);
        task.setNotifyStatus(NotifyTask.STATUS_ERROR);
        task.setParameterJson("{}");
        task.setUuid("T10004_trade_refund");
        notifyTaskRepository.save(task);
        NotifyTaskService service = new NotifyTaskService(
                notifyTaskRepository,
                new DynamicConfigService(new FakeDynamicConfigRepository()),
                TradeEventPublisher.noop(),
                new ObjectMapper());

        NotifyTaskExecuteResponse response = service.execNotifyTask("T10004_trade_refund");

        assertEquals(1, response.getWaitCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(NotifyTask.STATUS_SUCCESS, task.getNotifyStatus());
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
        public Optional<NotifyTask> queryNotifyTaskByUuid(String uuid) {
            return tasks.stream()
                    .filter(task -> uuid.equals(task.getUuid()))
                    .findFirst();
        }

        @Override
        public int updateNotifyTaskStatusProcessing(NotifyTask notifyTask) {
            if (notifyTask.getNotifyStatus() == NotifyTask.STATUS_INIT
                    || notifyTask.getNotifyStatus() == NotifyTask.STATUS_RETRY
                    || notifyTask.getNotifyStatus() == NotifyTask.STATUS_ERROR) {
                notifyTask.setNotifyStatus(NotifyTask.STATUS_PROCESSING);
                return 1;
            }
            return 0;
        }

        @Override
        public int updateNotifyTaskStatusSuccess(NotifyTask notifyTask) {
            if (notifyTask.getNotifyStatus() != NotifyTask.STATUS_PROCESSING) {
                return 0;
            }
            notifyTask.setNotifyStatus(NotifyTask.STATUS_SUCCESS);
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusRetry(NotifyTask notifyTask) {
            if (notifyTask.getNotifyStatus() != NotifyTask.STATUS_PROCESSING) {
                return 0;
            }
            notifyTask.setNotifyStatus(NotifyTask.STATUS_RETRY);
            return 1;
        }

        @Override
        public int updateNotifyTaskStatusError(NotifyTask notifyTask) {
            if (notifyTask.getNotifyStatus() != NotifyTask.STATUS_PROCESSING) {
                return 0;
            }
            notifyTask.setNotifyStatus(NotifyTask.STATUS_ERROR);
            return 1;
        }
    }

    private static class FakeTradeEventPublisher implements TradeEventPublisher {

        private final List<TradeEventMessageEntity> messages = new ArrayList<>();

        @Override
        public void publish(TradeEventMessageEntity message) {
            messages.add(message);
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
