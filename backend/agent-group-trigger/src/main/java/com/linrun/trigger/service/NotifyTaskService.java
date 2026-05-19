package com.linrun.trigger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.notify.response.NotifyTaskExecuteResponse;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.notify.adapter.NotifyTaskRepository;
import com.linrun.domain.notify.model.NotifyTask;
import com.linrun.domain.trade.adapter.TradeEventPublisher;
import com.linrun.domain.trade.model.TradeEventMessage;
import com.linrun.types.exception.AppException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotifyTaskService {

    private static final String DISPATCH_SUCCESS = "success";
    private static final String DISPATCH_ERROR = "error";
    private static final int MAX_RETRY_COUNT = 4;

    private final NotifyTaskRepository notifyTaskRepository;
    private final DynamicConfigService dynamicConfigService;
    private final TradeEventPublisher tradeEventPublisher;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public NotifyTaskService(NotifyTaskRepository notifyTaskRepository,
                             DynamicConfigService dynamicConfigService,
                             TradeEventPublisher tradeEventPublisher,
                             ObjectMapper objectMapper) {
        this.notifyTaskRepository = notifyTaskRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.tradeEventPublisher = tradeEventPublisher;
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
        task.setNotifyType(dynamicConfigService.getValue(
                DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_TYPE, NotifyTask.TYPE_HTTP));
        task.setNotifyMq(dynamicConfigService.getValue(
                DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_MQ, "agent.group.notify.group-settlement"));
        task.setNotifyUrl(dynamicConfigService.getValue(
                DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_URL, ""));
        task.setNotifyCount(0);
        task.setNotifyStatus(NotifyTask.STATUS_INIT);
        task.setParameterJson(groupSettlementPayload(team.getTeamId(), orderIds));
        task.setUuid(team.getTeamId() + "_" + NotifyTask.CATEGORY_TRADE_SETTLEMENT);
        notifyTaskRepository.save(task);
        return task;
    }

    public NotifyTaskExecuteResponse execNotifyJob() {
        return execNotifyTasks(notifyTaskRepository.queryUnExecutedNotifyTaskList(50));
    }

    public NotifyTaskExecuteResponse execNotifyJob(String teamId) {
        if (!StringUtils.hasText(teamId)) {
            return execNotifyJob();
        }
        return execNotifyTasks(notifyTaskRepository.queryUnExecutedNotifyTaskList(teamId));
    }

    private NotifyTaskExecuteResponse execNotifyTasks(List<NotifyTask> tasks) {
        int successCount = 0;
        int retryCount = 0;
        int errorCount = 0;
        for (NotifyTask task : tasks) {
            String dispatchResult = dispatch(task);
            if (DISPATCH_SUCCESS.equals(dispatchResult)) {
                successCount += notifyTaskRepository.updateNotifyTaskStatusSuccess(task);
            } else if (DISPATCH_ERROR.equals(dispatchResult)) {
                if (task.getNotifyCount() != null && task.getNotifyCount() >= MAX_RETRY_COUNT) {
                    errorCount += notifyTaskRepository.updateNotifyTaskStatusError(task);
                } else {
                    retryCount += notifyTaskRepository.updateNotifyTaskStatusRetry(task);
                }
            }
        }
        NotifyTaskExecuteResponse response = new NotifyTaskExecuteResponse();
        response.setWaitCount(tasks.size());
        response.setSuccessCount(successCount);
        response.setRetryCount(retryCount);
        response.setErrorCount(errorCount);
        return response;
    }

    private String dispatch(NotifyTask task) {
        try {
            if (NotifyTask.TYPE_HTTP.equalsIgnoreCase(task.getNotifyType())) {
                dispatchHttp(task);
                return DISPATCH_SUCCESS;
            }
            if (NotifyTask.TYPE_MQ.equalsIgnoreCase(task.getNotifyType())) {
                dispatchMq(task);
                return DISPATCH_SUCCESS;
            }
            return null;
        } catch (Exception e) {
            return DISPATCH_ERROR;
        }
    }

    private void dispatchHttp(NotifyTask task) {
        if (!StringUtils.hasText(task.getNotifyUrl()) || "none".equalsIgnoreCase(task.getNotifyUrl())) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(task.getParameterJson(), headers);
        try {
            restTemplate.postForEntity(task.getNotifyUrl(), entity, String.class);
        } catch (RestClientException e) {
            throw new AppException("NOTIFY_0001", "notify http dispatch failed");
        }
    }

    private void dispatchMq(NotifyTask task) {
        TradeEventMessage message = new TradeEventMessage();
        message.setFlowId(task.getUuid());
        message.setOrderId(task.getTeamId());
        message.setBizType("NOTIFY");
        message.setBizId(task.getTeamId());
        message.setEventType(task.getNotifyCategory());
        message.setToStatus("DISPATCHED");
        message.setRemark(task.getParameterJson());
        message.setCreateTime(LocalDateTime.now());
        tradeEventPublisher.publish(message);
    }

    private String groupSettlementPayload(String teamId, List<String> orderIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("teamId", teamId);
        payload.put("outTradeNoList", orderIds);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AppException("NOTIFY_0002", "notify payload build failed");
        }
    }
}
