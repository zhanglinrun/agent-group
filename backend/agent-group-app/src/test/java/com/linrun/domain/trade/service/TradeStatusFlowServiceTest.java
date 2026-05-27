package com.linrun.domain.trade.service;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.domain.trade.adapter.repository.TradeEventOutboxRepository;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStatusFlowServiceTest {

    @Test
    void shouldSaveTradeFlowAndOutboxAfterRecord() {
        FakeTradeStatusFlowRepository repository = new FakeTradeStatusFlowRepository();
        FakeTradeEventOutboxRepository outboxRepository = new FakeTradeEventOutboxRepository();
        TradeStatusFlowService service = new TradeStatusFlowService(repository, outboxRepository);

        service.record(
                "O10001",
                TradeStatusFlowService.BIZ_PAY,
                "P10001",
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                "WAIT_PAY",
                "SUCCESS",
                "pay success");

        assertEquals(1, repository.flows.size());
        assertEquals(1, outboxRepository.outboxes.size());
        TradeEventOutboxEntity outbox = outboxRepository.outboxes.get(0);
        assertEquals("O10001", outbox.getOrderId());
        assertEquals(TradeStatusFlowService.EVENT_PAY_SUCCESS, outbox.getEventType());
        assertTrue(outbox.getRoutingKey().startsWith("trade.event.pay."));
        TradeEventMessageEntity message = outbox.toMessage();
        assertEquals(outbox.getEventId(), message.getFlowId());
        assertEquals(outbox.getRoutingKey(), message.getRoutingKey());
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlowEntity> flows = new ArrayList<>();

        @Override
        public void save(TradeStatusFlowEntity flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlowEntity> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> flow.getOrderId().equals(orderId))
                    .toList();
        }
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
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_SUCCESS);
            outbox.setSendCount(outbox.getSendCount() + 1);
            return 1;
        }

        @Override
        public int updateStatusRetry(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() != TradeEventOutboxEntity.STATUS_PROCESSING) {
                return 0;
            }
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_RETRY);
            outbox.setSendCount(outbox.getSendCount() + 1);
            return 1;
        }

        @Override
        public int updateStatusDeadLetter(TradeEventOutboxEntity outbox) {
            if (outbox.getSendStatus() != TradeEventOutboxEntity.STATUS_PROCESSING) {
                return 0;
            }
            outbox.setSendStatus(TradeEventOutboxEntity.STATUS_DEAD_LETTER);
            outbox.setSendCount(outbox.getSendCount() + 1);
            return 1;
        }
    }
}
