package com.linrun.trigger.job;

import com.linrun.trigger.service.TradeCompensationService;
import com.linrun.trigger.service.ScheduledJobLockExecutor;
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

    public TradeTimeoutCompensationJob(TradeCompensationService tradeCompensationService) {
        this(tradeCompensationService,
                new ScheduledJobLockExecutor(com.linrun.domain.support.adapter.ScheduledJobLockRepository.noop()));
    }

    @Autowired
    public TradeTimeoutCompensationJob(TradeCompensationService tradeCompensationService,
                                       ScheduledJobLockExecutor scheduledJobLockExecutor) {
        this.tradeCompensationService = tradeCompensationService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
    }

    @Scheduled(cron = "${agent.group.trade.compensation.close-unpaid-cron:0 0/5 * * * ?}")
    public void closeTimeoutUnpaidOrders() {
        scheduledJobLockExecutor.execute("trade-timeout-compensation:close-unpaid", JOB_LOCK_LEASE_TIME, () -> {
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
            int closedCount = tradeCompensationService.closeTimeoutUnpaidOrders(deadline, 50);
            if (closedCount > 0) {
                LOGGER.info("timeout unpaid orders closed, count={}", closedCount);
            }
        });
    }

    @Scheduled(cron = "${agent.group.trade.compensation.refund-unsettled-group-cron:0 */1 * * * ?}")
    public void refundTimeoutUnsettledGroupOrders() {
        scheduledJobLockExecutor.execute("trade-timeout-compensation:refund-unsettled-group",
                JOB_LOCK_LEASE_TIME, () -> {
                    int refundCount = tradeCompensationService.refundTimeoutUnsettledGroupOrders(LocalDateTime.now(), 50);
                    if (refundCount > 0) {
                        LOGGER.info("timeout unsettled group orders refunded, count={}", refundCount);
                    }
                });
    }
}
