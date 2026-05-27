package com.linrun.trigger.job;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.domain.support.adapter.ScheduledJobLockRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledJobLockExecutorTest {

    @Test
    void shouldSkipTaskWhenLockIsHeld() {
        FakeScheduledJobLockRepository repository = new FakeScheduledJobLockRepository(Optional.empty());
        ScheduledJobLockExecutor executor = new ScheduledJobLockExecutor(repository);
        int[] runCount = new int[1];

        boolean executed = executor.execute("job-a", Duration.ofSeconds(60), () -> runCount[0]++);

        assertTrue(!executed);
        assertEquals(0, runCount[0]);
        assertEquals(0, repository.unlockCount);
    }

    @Test
    void shouldUnlockAfterTaskFailure() {
        FakeScheduledJobLockRepository repository = new FakeScheduledJobLockRepository(Optional.of("token-a"));
        ScheduledJobLockExecutor executor = new ScheduledJobLockExecutor(repository);

        assertThrows(IllegalStateException.class,
                () -> executor.execute("job-a", Duration.ofSeconds(60), () -> {
                    throw new IllegalStateException("failed");
                }));

        assertEquals(1, repository.unlockCount);
        assertEquals("job-a", repository.unlockName);
        assertEquals("token-a", repository.unlockToken);
    }

    private static class FakeScheduledJobLockRepository implements ScheduledJobLockRepository {

        private final Optional<String> token;
        private int unlockCount;
        private String unlockName;
        private String unlockToken;

        private FakeScheduledJobLockRepository(Optional<String> token) {
            this.token = token;
        }

        @Override
        public Optional<String> tryLock(String lockName, Duration leaseTime) {
            return token;
        }

        @Override
        public void unlock(String lockName, String lockToken) {
            unlockCount++;
            unlockName = lockName;
            unlockToken = lockToken;
        }
    }
}
