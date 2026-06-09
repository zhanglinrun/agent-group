package com.linrun.domain.trade.service.payment;

import com.linrun.domain.trade.adapter.repository.PaymentWebhookReplayRepository;
import com.linrun.domain.trade.model.payment.PaymentChannel;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentWebhookReplayGuard {

    private final long replayWindowSeconds;
    private final PaymentWebhookReplayRepository replayRepository;
    private final Map<String, LocalDateTime> processingEvents = new ConcurrentHashMap<>();

    public PaymentWebhookReplayGuard(long replayWindowSeconds) {
        this.replayWindowSeconds = replayWindowSeconds;
        this.replayRepository = null;
    }

    @Autowired
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
            throw new AppException("PAY_0008", "真实支付回调缺少时间成);
        }
        LocalDateTime now = LocalDateTime.now();
        long ageSeconds = Math.abs(Duration.between(webhookTime, now).toSeconds());
        if (ageSeconds > replayWindowSeconds) {
            throw new AppException("PAY_0009", "支付回调已超过防重放时间�?);
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















