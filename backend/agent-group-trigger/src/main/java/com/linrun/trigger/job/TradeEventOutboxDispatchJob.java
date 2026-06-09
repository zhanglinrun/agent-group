package com.linrun.trigger.job;

import com.linrun.api.dto.TradeEventOutboxDispatchResponse;
import com.linrun.trigger.job.ScheduledJobLockExecutor;
import com.linrun.domain.trade.service.TradeEventOutboxDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "agent.group.trade.event.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TradeEventOutboxDispatchJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeEventOutboxDispatchJob.class);
    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);

    private final TradeEventOutboxDispatchService tradeEventOutboxDispatchService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;

    public TradeEventOutboxDispatchJob(TradeEventOutboxDispatchService tradeEventOutboxDispatchService) {
        this(tradeEventOutboxDispatchService,
                new ScheduledJobLockExecutor(com.linrun.domain.support.adapter.ScheduledJobLockRepository.noop()));
    }

    @Autowired
    public TradeEventOutboxDispatchJob(TradeEventOutboxDispatchService tradeEventOutboxDispatchService,
                                       ScheduledJobLockExecutor scheduledJobLockExecutor) {
        this.tradeEventOutboxDispatchService = tradeEventOutboxDispatchService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
    }

    @Scheduled(cron = "${agent.group.trade.event.outbox.cron:0 */1 * * * ?}")
    public void exec() {
        scheduledJobLockExecutor.execute("trade-event-outbox-dispatch", JOB_LOCK_LEASE_TIME, () -> {
            TradeEventOutboxDispatchResponse response = tradeEventOutboxDispatchService.execDispatchJob();
            if (response.getWaitCount() > 0) {
                LOGGER.info("trade event outbox dispatched, wait={}, success={}, retry={}, deadLetter={}",
                        response.getWaitCount(),
                        response.getSuccessCount(),
                        response.getRetryCount(),
                        response.getDeadLetterCount());
            }
        });
    }
}















