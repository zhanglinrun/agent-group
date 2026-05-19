package com.linrun.trigger.job;

import com.linrun.api.notify.response.NotifyTaskExecuteResponse;
import com.linrun.trigger.service.NotifyTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.notify.task", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GroupBuyNotifyTaskJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupBuyNotifyTaskJob.class);

    private final NotifyTaskService notifyTaskService;

    public GroupBuyNotifyTaskJob(NotifyTaskService notifyTaskService) {
        this.notifyTaskService = notifyTaskService;
    }

    @Scheduled(cron = "${agent.group.notify.task.cron:0 */1 * * * ?}")
    public void exec() {
        NotifyTaskExecuteResponse response = notifyTaskService.execNotifyJob();
        if (response.getWaitCount() > 0) {
            LOGGER.info("group buy notify task executed, wait={}, success={}, retry={}, error={}",
                    response.getWaitCount(),
                    response.getSuccessCount(),
                    response.getRetryCount(),
                    response.getErrorCount());
        }
    }
}
