package com.linrun.trigger.service;

import com.linrun.domain.payment.adapter.PaymentWebhookReplayRepository;
import com.linrun.domain.payment.model.PaymentChannel;
import com.linrun.domain.payment.model.PaymentWebhookResult;
import com.linrun.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentWebhookReplayGuard {

    @Value("${agent.group.payment.webhook.replay-window-seconds:300}")
    private long replayWindowSeconds = 300L;
    @Resource
    private PaymentWebhookReplayRepository replayRepository;
    private final Map<String, LocalDateTime> processingEvents = new ConcurrentHashMap<>();

    public PaymentWebhookReplayGuard() {
    }

    public PaymentWebhookReplayGuard(long replayWindowSeconds) {
        this.replayWindowSeconds = replayWindowSeconds;
        this.replayRepository = null;
    }

    public PaymentWebhookReplayGuard(
            @Value("${agent.group.payment.webhook.replay-window-seconds:300}") long replayWindowSeconds,
            PaymentWebhookReplayRepository replayRepository) {
        this.replayWindowSeconds = replayWindowSeconds;
        this.replayRepository = replayRepository;
    }

    public boolean acquireProcessingLock(PaymentChannel channel, PaymentWebhookResult result) {
        if (PaymentChannel.MOCK_PAY.equals(channel)) {
            return true;
        }
        LocalDateTime now = validateWebhookTime(result);
        String replayKey = replayKey(channel, result);
        if (replayRepository != null) {
            return replayRepository.acquireProcessingLock(replayKey, Duration.ofSeconds(replayWindowSeconds));
        }
        cleanup(now);
        return processingEvents.putIfAbsent(replayKey, now) == null;
    }

    public void releaseProcessingLock(PaymentChannel channel, PaymentWebhookResult result) {
        if (PaymentChannel.MOCK_PAY.equals(channel)) {
            return;
        }
        String replayKey = replayKey(channel, result);
        if (replayRepository != null) {
            replayRepository.releaseProcessingLock(replayKey);
            return;
        }
        processingEvents.remove(replayKey);
    }

    private LocalDateTime validateWebhookTime(PaymentWebhookResult result) {
        LocalDateTime webhookTime = result.getWebhookTime();
        if (webhookTime == null) {
            throw new AppException("PAY_0008", "真实支付回调缺少时间戳");
        }
        LocalDateTime now = LocalDateTime.now();
        long ageSeconds = Math.abs(Duration.between(webhookTime, now).toSeconds());
        if (ageSeconds > replayWindowSeconds) {
            throw new AppException("PAY_0009", "支付回调已超过防重放时间窗");
        }
        return now;
    }

    private String replayKey(PaymentChannel channel, PaymentWebhookResult result) {
        if (StringUtils.hasText(result.getWebhookEventId())) {
            return channel.name() + ":" + result.getWebhookEventId();
        }
        return channel.name() + ":" + result.getPayOrderId() + ":" + result.getGatewayTradeNo();
    }

    private void cleanup(LocalDateTime now) {
        processingEvents.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).toSeconds() > replayWindowSeconds);
    }
}
