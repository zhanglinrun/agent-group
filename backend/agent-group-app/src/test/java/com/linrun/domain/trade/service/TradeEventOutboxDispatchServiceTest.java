package com.linrun.domain.trade.service;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.api.dto.TradeEventOutboxDispatchResponse;
import com.linrun.domain.trade.adapter.repository.TradeEventOutboxRepository;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeEventOutboxDispatchServiceTest {

    @Test
    void shouldDispatchPendingOutboxSuccessfully() {
        FakeTradeEventOutboxRepository repository = new FakeTradeEventOutboxRepository();
        TradeEventOutboxEntity outbox = outbox("E10001", 0, TradeEventOutboxEntity.STATUS_INIT);
        repository.save(outbox);
        FakeTradeEventPublisher publisher = new FakeTradeEventPublisher(false);
        TradeEventOutboxDispatchService service = new TradeEventOutboxDispatchService(repository, publisher);

        TradeEventOutboxDispatchResponse response = service.execDispatchJob();

        assertEquals(1, response.getWaitCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getRetryCount());
        assertEquals(0, response.getDeadLetterCount());
        assertEquals(TradeEventOutboxEntity.STATUS_SUCCESS, outbox.getSendStatus());
        assertEquals(1, publisher.messages.size());
    }

    @Test
    void shouldRetryThenMoveToDeadLetterAfterMaxFailures() {
        FakeTradeEventOutboxRepository repository = new FakeTradeEventOutboxRepository();
        TradeEventOutboxEntity outbox = outbox("E10002", 3, TradeEventOutboxEntity.STATUS_INIT);
        repository.save(outbox);
        FakeTradeEventPublisher publisher = new FakeTradeEventPublisher(true);
        TradeEventOutboxDispatchService service = new TradeEventOutboxDispatchService(repository, publisher);

        TradeEventOutboxDispatchResponse retryResponse = service.execDispatchJob();
        assertEquals(1, retryResponse.getRetryCount());
        assertEquals(TradeEventOutboxEntity.STATUS_RETRY, outbox.getSendStatus());

        TradeEventOutboxDispatchResponse deadLetterResponse = service.execDispatchJob();
        assertEquals(1, deadLetterResponse.getDeadLetterCount());
        assertEquals(TradeEventOutboxEntity.STATUS_DEAD_LETTER, outbox.getSendStatus());
    }

    private TradeEventOutboxEntity outbox(String eventId, int sendCount, int sendStatus) {
        TradeEventOutboxEntity outbox = new TradeEventOutboxEntity();
        outbox.setEventId(eventId);
        outbox.setOrderId("O" + eventId);
        outbox.setBizType(TradeStatusFlowService.BIZ_PAY);
        outbox.setBizId("P" + eventId);
        outbox.setEventType(TradeStatusFlowService.EVENT_PAY_SUCCESS);
        outbox.setRoutingKey(TradeEventMessageEntity.defaultRoutingKey(
                TradeStatusFlowService.BIZ_PAY,
                TradeStatusFlowService.EVENT_PAY_SUCCESS));
        outbox.setFromStatus("WAIT_PAY");
        outbox.setToStatus("SUCCESS");
        outbox.setRemark("remark");
        outbox.setSendCount(sendCount);
        outbox.setSendStatus(sendStatus);
        outbox.setCreateTime(LocalDateTime.now());
        return outbox;
    }

    private static class FakeTradeEventOutboxRepository implements TradeEventOutboxRepository {

        private final List<TradeEventOutboxEntity> outboxes = new ArrayList<>();

        @Override
        public void save(TradeEventOutboxEntity outbox) {
            outboxes.add(outbox);
        }

        @Override
        public List<TradeEventOutboxEntity> queryPending(int limit) {
            return outboxes.stream()
                    .filter(outbox -> outbox.getSendStatus() == TradeEventOutboxEntity.STATUS_INIT
                            || outbox.getSendStatus() == TradeEventOutboxEntity.STATUS_RETRY)
                    .limit(limit)
                    .toList();
        }

        @Override
        public int updateStatusProcessing(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() == TradeEventOutboxEntity.STATUS_INIT
                    || outbox.getSendStatus() == TradeEventOutboxEntity.STATUS_RETRY) {
                outbox.setSendStatus(TradeEventOutboxEntity.STATUS_PROCESSING);
                return 1;
            }
            return 0;
        }

        @Override
        public int updateStatusSuccess(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() != TradeEventOutboxEntity.STATUS_PROCESSING) {
                return 0;
            }
            outbox.setSendCount(outbox.getSendCount() + 1);
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_SUCCESS);
            return 1;
        }

        @Override
        public int updateStatusRetry(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() != TradeEventOutboxEntity.STATUS_PROCESSING) {
                return 0;
            }
            outbox.setSendCount(outbox.getSendCount() + 1);
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_RETRY);
            return 1;
        }

        @Override
        public int updateStatusDeadLetter(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() != TradeEventOutboxEntity.STATUS_PROCESSING) {
                return 0;
            }
            outbox.setSendCount(outbox.getSendCount() + 1);
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_DEAD_LETTER);
            return 1;
        }
    }

    private static class FakeTradeEventPublisher implements TradeEventPublisher {

        private final boolean fail;
        private final List<TradeEventMessageEntity> messages = new ArrayList<>();

        private FakeTradeEventPublisher(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void publish(TradeEventMessageEntity message) {
            messages.add(message);
            if (fail) {
                throw new IllegalStateException("mq unavailable");
            }
        }
    }
}















