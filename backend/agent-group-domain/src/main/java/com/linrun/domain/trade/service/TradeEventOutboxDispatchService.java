package com.linrun.domain.trade.service;

import com.linrun.api.dto.TradeEventOutboxDispatchResponse;
import com.linrun.domain.trade.adapter.repository.TradeEventOutboxRepository;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeEventOutboxDispatchService {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 4;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    private final TradeEventOutboxRepository tradeEventOutboxRepository;
    private final TradeEventPublisher tradeEventPublisher;

    public TradeEventOutboxDispatchService(TradeEventOutboxRepository tradeEventOutboxRepository,
                                           TradeEventPublisher tradeEventPublisher) {
        this.tradeEventOutboxRepository = tradeEventOutboxRepository;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public TradeEventOutboxDispatchResponse execDispatchJob() {
        return execDispatchTasks(tradeEventOutboxRepository.queryPending(DEFAULT_BATCH_SIZE));
    }

    private TradeEventOutboxDispatchResponse execDispatchTasks(List<TradeEventOutboxEntity> outboxes) {
        int successCount = 0;
        int retryCount = 0;
        int deadLetterCount = 0;
        for (TradeEventOutboxEntity outbox : outboxes) {
            if (tradeEventOutboxRepository.updateStatusProcessing(outbox) != 1) {
                continue;
            }
            try {
                tradeEventPublisher.publish(outbox.toMessage());
                successCount += tradeEventOutboxRepository.updateStatusSuccess(outbox);
            } catch (Exception e) {
                outbox.setLastError(trimError(e));
                if (shouldMarkDeadLetter(outbox)) {
                    deadLetterCount += tradeEventOutboxRepository.updateStatusDeadLetter(outbox);
                } else {
                    retryCount += tradeEventOutboxRepository.updateStatusRetry(outbox);
                }
            }
        }
        TradeEventOutboxDispatchResponse response = new TradeEventOutboxDispatchResponse();
        response.setWaitCount(outboxes.size());
        response.setSuccessCount(successCount);
        response.setRetryCount(retryCount);
        response.setDeadLetterCount(deadLetterCount);
        return response;
    }

    private boolean shouldMarkDeadLetter(TradeEventOutboxEntity outbox) {
        return outbox.getSendCount() != null && outbox.getSendCount() >= MAX_RETRY_COUNT;
    }

    private String trimError(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
