package com.linrun.trigger.job;

import com.linrun.api.dto.NotifyTaskExecuteResponse;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.trigger.job.ScheduledJobLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "agent.group.notify.task", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GroupBuyNotifyTaskJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupBuyNotifyTaskJob.class);
    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);

    private final NotifyTaskService notifyTaskService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;

    public GroupBuyNotifyTaskJob(NotifyTaskService notifyTaskService) {
        this(notifyTaskService,
                new ScheduledJobLockExecutor(com.linrun.domain.support.adapter.ScheduledJobLockRepository.noop()));
    }

    @Autowired
    public GroupBuyNotifyTaskJob(NotifyTaskService notifyTaskService,
                                 ScheduledJobLockExecutor scheduledJobLockExecutor) {
        this.notifyTaskService = notifyTaskService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
    }

    @Scheduled(cron = "${agent.group.notify.task.cron:0 */1 * * * ?}")
    public void exec() {
        scheduledJobLockExecutor.execute("group-buy-notify-task", JOB_LOCK_LEASE_TIME, () -> {
            NotifyTaskExecuteResponse response = notifyTaskService.execNotifyJob();
            if (response.getWaitCount() > 0) {
                LOGGER.info("group buy notify task executed, wait={}, success={}, retry={}, error={}",
                        response.getWaitCount(),
                        response.getSuccessCount(),
                        response.getRetryCount(),
                        response.getErrorCount());
            }
        });
    }
}















