package com.linrun.domain.trade.adapter.repository;

import java.time.Duration;

public interface PaymentWebhookReplayRepository {

    boolean acquireProcessingLock(String replayKey, Duration ttl);

    void releaseProcessingLock(String replayKey);
}















