package com.linrun.domain.payment.adapter;

import java.time.Duration;

public interface PaymentWebhookReplayRepository {

    boolean acquireProcessingLock(String replayKey, Duration ttl);

    void releaseProcessingLock(String replayKey);
}
