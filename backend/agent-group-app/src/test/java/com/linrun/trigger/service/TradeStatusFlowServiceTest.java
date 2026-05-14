package com.linrun.trigger.service;

import com.linrun.domain.trade.adapter.TradeEventPublisher;
import com.linrun.domain.trade.adapter.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.TradeEventMessage;
import com.linrun.domain.trade.model.TradeStatusFlow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeStatusFlowServiceTest {

    @Test
    void shouldPublishTradeEventAfterFlowSaved() {
        FakeTradeStatusFlowRepository repository = new FakeTradeStatusFlowRepository();
        FakeTradeEventPublisher publisher = new FakeTradeEventPublisher();
        TradeStatusFlowService service = new TradeStatusFlowService(repository, publisher);

        service.record(
                "O10001",
                TradeStatusFlowService.BIZ_PAY,
                "P10001",
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                "WAIT_PAY",
                "SUCCESS",
                "pay success");

        assertEquals(1, repository.flows.size());
        assertEquals(1, publisher.messages.size());
        assertEquals("O10001", publisher.messages.get(0).getOrderId());
        assertEquals(TradeStatusFlowService.EVENT_PAY_SUCCESS, publisher.messages.get(0).getEventType());
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlow> flows = new ArrayList<>();

        @Override
        public void save(TradeStatusFlow flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlow> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> flow.getOrderId().equals(orderId))
                    .toList();
        }
    }

    private static class FakeTradeEventPublisher implements TradeEventPublisher {

        private final List<TradeEventMessage> messages = new ArrayList<>();

        @Override
        public void publish(TradeEventMessage message) {
            messages.add(message);
        }
    }
}
