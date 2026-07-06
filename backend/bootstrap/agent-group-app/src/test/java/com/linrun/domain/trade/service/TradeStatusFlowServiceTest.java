package com.linrun.domain.trade.service;

import com.linrun.api.dto.TradeStatusFlowDTO;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStatusFlowServiceTest {

    @Test
    void shouldSaveTradeFlowAndPublishAfterRecord() {
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
        TradeEventMessageEntity message = publisher.messages.get(0);
        assertEquals("O10001", message.getOrderId());
        assertEquals(TradeStatusFlowService.EVENT_PAY_SUCCESS, message.getEventType());
        assertTrue(message.getRoutingKey().startsWith("trade.event.pay."));
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

    private static class FakeTradeEventPublisher implements TradeEventPublisher {

        private final List<TradeEventMessageEntity> messages = new ArrayList<>();

        @Override
        public void publish(TradeEventMessageEntity message) {
            messages.add(message);
        }
    }
}
