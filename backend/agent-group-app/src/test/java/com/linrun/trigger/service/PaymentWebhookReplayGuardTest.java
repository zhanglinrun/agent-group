package com.linrun.trigger.service;

import com.linrun.domain.payment.adapter.PaymentWebhookReplayRepository;
import com.linrun.domain.payment.model.PaymentChannel;
import com.linrun.domain.payment.model.PaymentWebhookResult;
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
