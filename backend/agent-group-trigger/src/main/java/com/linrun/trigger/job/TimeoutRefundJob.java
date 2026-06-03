package com.linrun.trigger.job;

import com.linrun.domain.support.adapter.ScheduledJobLockRepository;
import com.linrun.trigger.job.ScheduledJobLockExecutor;
import com.linrun.domain.trade.service.TradeCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "agent.group.trade.timeout-refund", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TimeoutRefundJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutRefundJob.class);
    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);

    private final TradeCompensationService tradeCompensationService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;

    public TimeoutRefundJob(TradeCompensationService tradeCompensationService) {
        this(tradeCompensationService, new ScheduledJobLockExecutor(ScheduledJobLockRepository.noop()));
    }

    @Autowired
    public TimeoutRefundJob(TradeCompensationService tradeCompensationService,
                            ScheduledJobLockExecutor scheduledJobLockExecutor) {
        this.tradeCompensationService = tradeCompensationService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
    }

    @Scheduled(cron = "${agent.group.trade.timeout-refund.cron:0 */1 * * * ?}")
    public void refundTimeoutUnsettledGroupOrders() {
        scheduledJobLockExecutor.execute("timeout-refund:unsettled-group",
                JOB_LOCK_LEASE_TIME, () -> {
                    LocalDateTime now = LocalDateTime.now();
                    int closedCount = tradeCompensationService.closeTimeoutUnsettledGroupOrders(now, 50);
                    if (closedCount > 0) {
                        LOGGER.info("timeout unpaid group orders closed, count={}", closedCount);
                    }
                    int refundCount = tradeCompensationService.refundTimeoutUnsettledGroupOrders(now, 50);
                    if (refundCount > 0) {
                        LOGGER.info("timeout group orders refunded, count={}", refundCount);
                    }
                });
    }
}
