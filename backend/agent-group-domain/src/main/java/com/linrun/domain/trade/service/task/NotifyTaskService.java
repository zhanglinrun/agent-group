package com.linrun.domain.trade.service.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.NotifyTaskExecuteResponse;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.trade.adapter.repository.NotifyTaskRepository;
import com.linrun.domain.trade.adapter.port.TradeNotifyPort;
import com.linrun.domain.trade.model.notify.NotifyConfig;
import com.linrun.domain.trade.model.notify.NotifyTask;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.support.lock.DistributedLock;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼团结果对外通知任务。
 *
 * 语义说明：这里通知的是"成团结算 / 退款完成"这类业务结果，发给下游订阅方
 * （默认投递到 MQ 的 notify 路由，由 {@code RabbitTradeEventConfiguration} 声明的队列承接；
 * 也可配置为 HTTP 推送到外部系统）。它和支付网关的支付回调是两回事——支付回调
 * 在 {@code PaymentService#handleWebhook} 里，带验签和防重放，方向是网关调我们；
 * 这里方向是我们通知别人，失败靠 {@code GroupBuyNotifyTaskJob} 定时重试。
 */
@Service
public class NotifyTaskService {

    private static final String DISPATCH_SUCCESS = "success";
    private static final String DISPATCH_ERROR = "error";
    private static final int MAX_RETRY_COUNT = 4;

    private final NotifyTaskRepository notifyTaskRepository;
    private final DynamicConfigService dynamicConfigService;
    private final TradeNotifyPort tradeNotifyPort;
    private final ObjectMapper objectMapper;

    public NotifyTaskService(NotifyTaskRepository notifyTaskRepository,
                             DynamicConfigService dynamicConfigService,
                             TradeEventPublisher tradeEventPublisher,
                             ObjectMapper objectMapper) {
        this(notifyTaskRepository, dynamicConfigService, (TradeNotifyPort) task -> {
            if (NotifyTask.TYPE_MQ.equalsIgnoreCase(task.getNotifyType())) {
                com.linrun.domain.trade.model.entity.TradeEventMessageEntity message =
                        new com.linrun.domain.trade.model.entity.TradeEventMessageEntity();
                message.setFlowId(task.getUuid());
                message.setOrderId(task.getTeamId());
                message.setBizType("NOTIFY");
                message.setBizId(task.getTeamId());
                message.setEventType(task.getNotifyCategory());
                message.setRoutingKey(task.getNotifyMq());
                message.setToStatus("DISPATCHED");
                message.setRemark(task.getParameterJson());
                message.setCreateTime(java.time.LocalDateTime.now());
                tradeEventPublisher.publish(message);
            }
        }, objectMapper);
    }

    @Autowired
    public NotifyTaskService(NotifyTaskRepository notifyTaskRepository,
                             DynamicConfigService dynamicConfigService,
                             TradeNotifyPort tradeNotifyPort,
                             ObjectMapper objectMapper) {
        this.notifyTaskRepository = notifyTaskRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.tradeNotifyPort = tradeNotifyPort == null ? TradeNotifyPort.noop() : tradeNotifyPort;
        this.objectMapper = objectMapper;
    }

    public NotifyTask createGroupSettlementTask(GroupBuyTeam team, List<String> orderIds) {
        if (team == null || orderIds == null || orderIds.isEmpty()) {
            return null;
        }
        NotifyTask task = new NotifyTask();
        task.setActivityId(team.getActivityId());
        task.setTeamId(team.getTeamId());
        task.setNotifyCategory(NotifyTask.CATEGORY_TRADE_SETTLEMENT);
        task.applyConfig(settlementNotifyConfig());
        task.setNotifyCount(0);
        task.setNotifyStatus(NotifyTask.STATUS_INIT);
        task.setParameterJson(groupSettlementPayload(team, orderIds));
        task.setUuid(team.getTeamId() + "_" + NotifyTask.CATEGORY_TRADE_SETTLEMENT);
        notifyTaskRepository.save(task);
        return task;
    }

    public NotifyTask createGroupRefundTask(GroupBuyCompensationResponse response) {
        if (response == null
                || !StringUtils.hasText(response.getOrderId())
                || !StringUtils.hasText(response.getRefundId())) {
            return null;
        }
        NotifyTask task = new NotifyTask();
        task.setActivityId(StringUtils.hasText(response.getActivityId()) ? response.getActivityId() : "UNKNOWN");
        task.setTeamId(StringUtils.hasText(response.getTeamId()) ? response.getTeamId() : response.getOrderId());
        task.setNotifyCategory(NotifyTask.CATEGORY_TRADE_REFUND);
        task.applyConfig(refundNotifyConfig());
        task.setNotifyCount(0);
        task.setNotifyStatus(NotifyTask.STATUS_INIT);
        task.setParameterJson(groupRefundPayload(response));
        task.setUuid(response.getOrderId() + "_" + NotifyTask.CATEGORY_TRADE_REFUND);
        notifyTaskRepository.save(task);
        return task;
    }

    public NotifyTaskExecuteResponse execNotifyJob() {
        return execNotifyTasks(notifyTaskRepository.queryUnExecutedNotifyTaskList(50));
    }

    @DistributedLock(key = "'notify:job:team:' + (#p0 == null || #p0.isBlank() ? 'ALL' : #p0)",
            waitTime = 1L, leaseTime = 30L)
    public NotifyTaskExecuteResponse execNotifyJob(String teamId) {
        if (!StringUtils.hasText(teamId)) {
            return execNotifyJob();
        }
        return execNotifyTasks(notifyTaskRepository.queryUnExecutedNotifyTaskList(teamId));
    }

    @DistributedLock(key = "'notify:job:uuid:' + #p0", waitTime = 1L, leaseTime = 30L)
    public NotifyTaskExecuteResponse execNotifyTask(String uuid) {
        if (!StringUtils.hasText(uuid)) {
            throw new AppException("NOTIFY_0003", "notify task uuid cannot be blank");
        }
        NotifyTask task = notifyTaskRepository.queryNotifyTaskByUuid(uuid)
                .orElseThrow(() -> new AppException("NOTIFY_0004", "notify task not found"));
        return execNotifyTasks(List.of(task));
    }

    private NotifyTaskExecuteResponse execNotifyTasks(List<NotifyTask> tasks) {
        int successCount = 0;
        int retryCount = 0;
        int errorCount = 0;
        for (NotifyTask task : tasks) {
            if (notifyTaskRepository.updateNotifyTaskStatusProcessing(task) != 1) {
                continue;
            }
            String dispatchResult = dispatch(task);
            if (DISPATCH_SUCCESS.equals(dispatchResult)) {
                successCount += notifyTaskRepository.updateNotifyTaskStatusSuccess(task);
            } else if (shouldMarkError(task)) {
                errorCount += notifyTaskRepository.updateNotifyTaskStatusError(task);
            } else {
                retryCount += notifyTaskRepository.updateNotifyTaskStatusRetry(task);
            }
        }
        NotifyTaskExecuteResponse response = new NotifyTaskExecuteResponse();
        response.setWaitCount(tasks.size());
        response.setSuccessCount(successCount);
        response.setRetryCount(retryCount);
        response.setErrorCount(errorCount);
        return response;
    }

    private boolean shouldMarkError(NotifyTask task) {
        return task.getNotifyCount() != null && task.getNotifyCount() >= MAX_RETRY_COUNT;
    }

    private String dispatch(NotifyTask task) {
        try {
            tradeNotifyPort.dispatch(task);
            return DISPATCH_SUCCESS;
        } catch (Exception e) {
            return DISPATCH_ERROR;
        }
    }

    private NotifyConfig settlementNotifyConfig() {
        return NotifyConfig.of(
                dynamicConfigService.getValue(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_TYPE, NotifyTask.TYPE_MQ),
                dynamicConfigService.getValue(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_MQ, "agent.group.notify.group-settlement"),
                dynamicConfigService.getValue(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_URL, ""));
    }

    private NotifyConfig refundNotifyConfig() {
        return NotifyConfig.of(
                dynamicConfigService.getValue(DynamicConfigService.GROUP_REFUND_NOTIFY_TYPE, NotifyTask.TYPE_MQ),
                dynamicConfigService.getValue(DynamicConfigService.GROUP_REFUND_NOTIFY_MQ, "agent.group.notify.group-refund"),
                dynamicConfigService.getValue(DynamicConfigService.GROUP_REFUND_NOTIFY_URL, ""));
    }

    private String groupSettlementPayload(GroupBuyTeam team, List<String> orderIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("teamId", team.getTeamId());
        payload.put("activityId", team.getActivityId());
        payload.put("teamStatus", team.getTeamStatus() == null ? "" : team.getTeamStatus().name());
        payload.put("orderCount", orderIds.size());
        payload.put("outTradeNoList", orderIds);
        payload.put("settledAt", java.time.LocalDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AppException("NOTIFY_0002", "notify payload build failed");
        }
    }

    private String groupRefundPayload(GroupBuyCompensationResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", response.getOrderId());
        payload.put("payOrderId", response.getPayOrderId());
        payload.put("refundId", response.getRefundId());
        payload.put("activityId", response.getActivityId());
        payload.put("teamId", response.getTeamId());
        payload.put("orderStatus", response.getOrderStatus());
        payload.put("payStatus", response.getPayStatus());
        payload.put("refundAmount", response.getRefundAmount());
        payload.put("finishTime", response.getFinishTime());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AppException("NOTIFY_0002", "notify payload build failed");
        }
    }
}















