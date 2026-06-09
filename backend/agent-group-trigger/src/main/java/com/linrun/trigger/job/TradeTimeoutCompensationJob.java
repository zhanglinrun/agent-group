package com.linrun.trigger.job;

import com.linrun.domain.trade.service.TradeCompensationService;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.trigger.job.ScheduledJobLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "agent.group.trade.compensation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TradeTimeoutCompensationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeTimeoutCompensationJob.class);
    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);

    private final TradeCompensationService tradeCompensationService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;
    private final DynamicConfigService dynamicConfigService;

    public TradeTimeoutCompensationJob(TradeCompensationService tradeCompensationService) {
        this(tradeCompensationService,
                new ScheduledJobLockExecutor(com.linrun.domain.support.adapter.ScheduledJobLockRepository.noop()),
                null);
    }

    @Autowired
    public TradeTimeoutCompensationJob(TradeCompensationService tradeCompensationService,
                                       ScheduledJobLockExecutor scheduledJobLockExecutor,
                                       DynamicConfigService dynamicConfigService) {
        this.tradeCompensationService = tradeCompensationService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
        this.dynamicConfigService = dynamicConfigService;
    }

    @Scheduled(cron = "${agent.group.trade.compensation.close-unpaid-cron:0 0/5 * * * ?}")
    public void closeTimeoutUnpaidOrders() {
        scheduledJobLockExecutor.execute("trade-timeout-compensation:close-unpaid", JOB_LOCK_LEASE_TIME, () -> {
            if (dynamicConfigService == null || dynamicConfigService.isPaymentQueryCompensationOpen()) {
                LocalDateTime queryDeadline = LocalDateTime.now().minusMinutes(3);
                int limit = dynamicConfigService == null ? 50 : dynamicConfigService.paymentQueryCompensationLimit();
                int completedCount = tradeCompensationService.reconcileTimeoutPayWaitOrders(queryDeadline, limit);
                if (completedCount > 0) {
                    LOGGER.info("timeout pay-wait orders reconciled by gateway query, count={}", completedCount);
                }
            }
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
            int closedCount = tradeCompensationService.closeTimeoutUnpaidOrders(deadline, 50);
            if (closedCount > 0) {
                LOGGER.info("timeout unpaid orders closed, count={}", closedCount);
            }
        });
    }

}















