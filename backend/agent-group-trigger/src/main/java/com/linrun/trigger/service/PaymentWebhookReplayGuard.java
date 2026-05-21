package com.linrun.trigger.service;

import com.linrun.domain.payment.model.PaymentChannel;
import com.linrun.domain.payment.model.PaymentWebhookResult;
import com.linrun.types.exception.AppException;
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
    private final Map<String, LocalDateTime> acceptedEvents = new ConcurrentHashMap<>();

    public PaymentWebhookReplayGuard(
            @Value("${agent.group.payment.webhook.replay-window-seconds:300}") long replayWindowSeconds) {
        this.replayWindowSeconds = replayWindowSeconds;
    }

    public boolean markFirstSeen(PaymentChannel channel, PaymentWebhookResult result) {
        if (PaymentChannel.MOCK_PAY.equals(channel)) {
            return true;
        }
        LocalDateTime webhookTime = result.getWebhookTime();
        if (webhookTime == null) {
            throw new AppException("PAY_0008", "真实支付回调缺少时间戳");
        }
        LocalDateTime now = LocalDateTime.now();
        long ageSeconds = Math.abs(Duration.between(webhookTime, now).toSeconds());
        if (ageSeconds > replayWindowSeconds) {
            throw new AppException("PAY_0009", "支付回调已超过防重放时间窗");
        }
        cleanup(now);
        String replayKey = replayKey(channel, result);
        return acceptedEvents.putIfAbsent(replayKey, now) == null;
    }

    private String replayKey(PaymentChannel channel, PaymentWebhookResult result) {
        if (StringUtils.hasText(result.getWebhookEventId())) {
            return channel.name() + ":" + result.getWebhookEventId();
        }
        return channel.name() + ":" + result.getPayOrderId() + ":" + result.getGatewayTradeNo();
    }

    private void cleanup(LocalDateTime now) {
        acceptedEvents.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).toSeconds() > replayWindowSeconds);
    }
}
