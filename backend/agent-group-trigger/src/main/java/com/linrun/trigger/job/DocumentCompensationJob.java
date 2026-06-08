package com.linrun.trigger.job;

import com.linrun.domain.support.adapter.ScheduledJobLockRepository;
import com.linrun.trigger.http.agent.KnowledgeVectorOpsHandler;
import com.linrun.trigger.job.ScheduledJobLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "agent.group.knowledge.compensation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentCompensationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentCompensationJob.class);
    private static final Duration JOB_LOCK_LEASE_TIME = Duration.ofSeconds(60);

    private final KnowledgeVectorOpsHandler knowledgeVectorOpsService;
    private final ScheduledJobLockExecutor scheduledJobLockExecutor;

    public DocumentCompensationJob(KnowledgeVectorOpsHandler knowledgeVectorOpsService) {
        this(knowledgeVectorOpsService, new ScheduledJobLockExecutor(ScheduledJobLockRepository.noop()));
    }

    @Autowired
    public DocumentCompensationJob(KnowledgeVectorOpsHandler knowledgeVectorOpsService,
                                   ScheduledJobLockExecutor scheduledJobLockExecutor) {
        this.knowledgeVectorOpsService = knowledgeVectorOpsService;
        this.scheduledJobLockExecutor = scheduledJobLockExecutor;
    }

    @Scheduled(cron = "${agent.group.knowledge.compensation.embedding-cron:0 */5 * * * ?}")
    public void compensateFailedEmbedding() {
        scheduledJobLockExecutor.execute("knowledge-document:embedding-compensation",
                JOB_LOCK_LEASE_TIME, () -> {
                    var response = knowledgeVectorOpsService.compensateFailedEmbedding(20);
                    if (response.getFragmentCount() != null && response.getFragmentCount() > 0) {
                        LOGGER.info("document embedding compensation finished, fragments={}, success={}, failed={}",
                                response.getFragmentCount(), response.getSuccessCount(), response.getFailedCount());
                    }
                });
    }
}
