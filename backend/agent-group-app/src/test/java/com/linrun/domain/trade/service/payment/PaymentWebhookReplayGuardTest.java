package com.linrun.domain.trade.service.payment;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.domain.trade.adapter.repository.PaymentWebhookReplayRepository;
import com.linrun.domain.trade.model.payment.PaymentChannel;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentWebhookReplayGuardTest {

    @Test
    void shouldUseReplayRepositoryAsProcessingLockForRealPaymentWebhook() {
        FakeReplayRepository repository = new FakeReplayRepository();
        PaymentWebhookReplayGuard guard = new PaymentWebhookReplayGuard(300L, repository);
        PaymentWebhookResult result = PaymentWebhookResult.verified(
                "O10001",
                "P10001",
                "GT10001",
                LocalDateTime.now(),
                "EVT10001",
                LocalDateTime.now(),
                "verified");

        assertTrue(guard.acquireProcessingLock(PaymentChannel.ALIPAY, result));
        assertFalse(guard.acquireProcessingLock(PaymentChannel.ALIPAY, result));
        guard.releaseProcessingLock(PaymentChannel.ALIPAY, result);
        assertTrue(guard.acquireProcessingLock(PaymentChannel.ALIPAY, result));
        assertTrue(repository.lastTtl.equals(Duration.ofSeconds(300)));
        assertTrue(repository.keys.contains("ALIPAY:EVT10001"));
    }

    private static class FakeReplayRepository implements PaymentWebhookReplayRepository {

        private final Set<String> keys = new HashSet<>();
        private Duration lastTtl;

        @Override
        public boolean acquireProcessingLock(String replayKey, Duration ttl) {
            this.lastTtl = ttl;
            return keys.add(replayKey);
        }

        @Override
        public void releaseProcessingLock(String replayKey) {
            keys.remove(replayKey);
        }
    }
}
