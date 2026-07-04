package com.linrun.trigger.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.NotifyTaskExecuteResponse;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.trade.service.TradeCompensationService;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.types.exception.AppException;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TradeCompensationXxlJobHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeCompensationXxlJobHandler.class);

    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);
    private static final TypeReference<Map<String, String>> PARAM_TYPE = new TypeReference<>() {
    };

    private final NotifyTaskService notifyTaskService;
    private final TradeCompensationService tradeCompensationService;
    private final DynamicConfigService dynamicConfigService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;
    private final ObjectMapper objectMapper;

    public TradeCompensationXxlJobHandler(NotifyTaskService notifyTaskService,
                                          TradeCompensationService tradeCompensationService,
                                          DynamicConfigService dynamicConfigService,
                                          ScheduledJobLockExecutor scheduledJobLockExecutor,
                                          ObjectMapper objectMapper) {
        this.notifyTaskService = notifyTaskService;
        this.tradeCompensationService = tradeCompensationService;
        this.dynamicConfigService = dynamicConfigService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
        this.objectMapper = objectMapper;
    }

    @XxlJob("groupBuyNotifyTaskJobHandler")
    public void groupBuyNotifyTaskJobHandler() {
        handleGroupBuyNotifyTask(XxlJobHelper.getJobParam());
    }

    void handleGroupBuyNotifyTask(String rawParam) {
        Map<String, String> params = parseParams(rawParam);
        String uuid = params.get("uuid");
        String teamId = params.get("teamId");
        if (StringUtils.hasText(uuid) && StringUtils.hasText(teamId)) {
            throw new AppException("NOTIFY_0005", "uuid and teamId cannot both be set");
        }
        executeWithLock("group-buy-notify-task", () -> {
            NotifyTaskExecuteResponse response;
            if (StringUtils.hasText(uuid)) {
                response = notifyTaskService.execNotifyTask(uuid);
            } else if (StringUtils.hasText(teamId)) {
                response = notifyTaskService.execNotifyJob(teamId);
            } else {
                response = notifyTaskService.execNotifyJob();
            }
            jobLog("group buy notify task executed, wait={}, success={}, retry={}, error={}",
                    response.getWaitCount(),
                    response.getSuccessCount(),
                    response.getRetryCount(),
                    response.getErrorCount());
            LOGGER.info("group buy notify task executed, uuid={}, teamId={}, wait={}, success={}, retry={}, error={}",
                    emptyToDash(uuid),
                    emptyToDash(teamId),
                    response.getWaitCount(),
                    response.getSuccessCount(),
                    response.getRetryCount(),
                    response.getErrorCount());
        });
    }

    @XxlJob("timeoutGroupRefundJobHandler")
    public void timeoutGroupRefundJobHandler() {
        handleTimeoutGroupRefund();
    }

    void handleTimeoutGroupRefund() {
        executeWithLock("timeout-refund:unsettled-group", () -> {
            LocalDateTime now = LocalDateTime.now();
            int batchSize = compensationBatchSize();
            int closedCount = tradeCompensationService.closeTimeoutUnsettledGroupOrders(now, batchSize);
            int refundCount = tradeCompensationService.refundTimeoutUnsettledGroupOrders(now, batchSize);
            jobLog("timeout group compensation executed, closed={}, refunded={}", closedCount, refundCount);
            LOGGER.info("timeout group compensation executed, closed={}, refunded={}", closedCount, refundCount);
        });
    }

    @XxlJob("paymentQueryCompensationJobHandler")
    public void paymentQueryCompensationJobHandler() {
        handlePaymentQueryCompensation(XxlJobHelper.getJobParam());
    }

    void handlePaymentQueryCompensation(String rawParam) {
        Map<String, String> params = parseParams(rawParam);
        String orderId = params.get("orderId");
        executeWithLock("trade-timeout-compensation:close-unpaid", () -> {
            if (StringUtils.hasText(orderId)) {
                boolean completed = tradeCompensationService.reconcilePayWaitOrder(orderId);
                jobLog("payment query compensation executed, orderId={}, completed={}", orderId, completed);
                LOGGER.info("payment query compensation executed, orderId={}, completed={}", orderId, completed);
                return;
            }
            int completedCount = 0;
            if (dynamicConfigService.isPaymentQueryCompensationOpen()) {
                LocalDateTime queryDeadline = LocalDateTime.now().minusMinutes(3);
                completedCount = tradeCompensationService.reconcileTimeoutPayWaitOrders(
                        queryDeadline, dynamicConfigService.paymentQueryCompensationLimit());
            }
            LocalDateTime closeDeadline = LocalDateTime.now().minusMinutes(30);
            int closedCount = tradeCompensationService.closeTimeoutUnpaidOrders(closeDeadline, compensationBatchSize());
            jobLog("payment query compensation executed, completed={}, closed={}",
                    completedCount, closedCount);
            LOGGER.info("payment query compensation executed, completed={}, closed={}",
                    completedCount, closedCount);
        });
    }

    private int compensationBatchSize() {
        return dynamicConfigService == null ? 50 : dynamicConfigService.tradeCompensationBatchSize();
    }

    private void executeWithLock(String lockName, Runnable task) {
        boolean executed = scheduledJobLockExecutor.execute(lockName, JOB_LOCK_LEASE_TIME, task);
        if (!executed) {
            jobLog("job skipped because another executor is running, lockName={}", lockName);
        }
    }

    private Map<String, String> parseParams(String rawParam) {
        if (!StringUtils.hasText(rawParam)) {
            return Map.of();
        }
        String param = rawParam.trim();
        try {
            if (param.startsWith("{")) {
                return objectMapper.readValue(param, PARAM_TYPE);
            }
            if (param.contains("=")) {
                return parseKeyValueParams(param);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("JOB_0001", "job param parse failed");
        }
        throw new AppException("JOB_0002", "job param must be JSON or key=value");
    }

    private Map<String, String> parseKeyValueParams(String param) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = param.split("[,;&]");
        for (String pair : pairs) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int splitIndex = pair.indexOf('=');
            if (splitIndex <= 0 || splitIndex == pair.length() - 1) {
                throw new AppException("JOB_0002", "job param must be JSON or key=value");
            }
            String key = pair.substring(0, splitIndex).trim();
            String value = pair.substring(splitIndex + 1).trim();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                throw new AppException("JOB_0002", "job param must be JSON or key=value");
            }
            result.put(key, value);
        }
        return result;
    }

    private String emptyToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private void jobLog(String pattern, Object... arguments) {
        try {
            XxlJobHelper.log(pattern, arguments);
        } catch (Exception e) {
            LOGGER.debug("xxl-job runtime log skipped, reason={}", e.getClass().getSimpleName());
        }
    }
}
