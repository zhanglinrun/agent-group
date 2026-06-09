package com.linrun.infrastructure.trade.event;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitTradeEventListenerTest {

    @Test
    void shouldSkipDuplicateConsumedMessage() {
        FakeTradeEventConsumeRecordRepository repository = new FakeTradeEventConsumeRecordRepository();
        RabbitTradeEventListener listener = new RabbitTradeEventListener(repository);
        TradeEventMessageEntity message = message("E20001");

        listener.consume(message);
        listener.consume(message);

        TradeEventConsumeRecordEntity record = repository.records.get("E20001");
        assertEquals(TradeEventConsumeRecordEntity.STATUS_CONSUMED, record.getConsumeStatus());
        assertEquals(1, record.getConsumeCount());
    }

    @Test
    void shouldMoveDeadLetterMessageToDeadStatus() {
        FakeTradeEventConsumeRecordRepository repository = new FakeTradeEventConsumeRecordRepository();
        RabbitTradeEventListener listener = new RabbitTradeEventListener(repository);
        TradeEventMessageEntity message = message("E20002");
        TradeEventConsumeRecordEntity record = TradeEventConsumeRecordEntity.fromMessage(message);
        record.setConsumeCount(4);
        record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_RETRY);
        repository.records.put(record.getEventId(), record);

        listener.consumeDeadLetter(message);

        assertEquals(TradeEventConsumeRecordEntity.STATUS_DEAD_LETTER, record.getConsumeStatus());
        assertEquals(5, record.getConsumeCount());
    }

    private TradeEventMessageEntity message(String eventId) {
        TradeEventMessageEntity message = new TradeEventMessageEntity();
        message.setFlowId(eventId);
        message.setOrderId("O" + eventId);
        message.setBizType(TradeStatusFlowService.BIZ_PAY);
        message.setBizId("P" + eventId);
        message.setEventType(TradeStatusFlowService.EVENT_PAY_SUCCESS);
        message.setRoutingKey(TradeEventMessageEntity.defaultRoutingKey(
                TradeStatusFlowService.BIZ_PAY,
                TradeStatusFlowService.EVENT_PAY_SUCCESS));
        message.setFromStatus("WAIT_PAY");
        message.setToStatus("SUCCESS");
        message.setRemark("remark");
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private static class FakeTradeEventConsumeRecordRepository implements TradeEventConsumeRecordRepository {

        private final Map<String, TradeEventConsumeRecordEntity> records = new HashMap<>();

        @Override
        public void save(TradeEventConsumeRecordEntity record) {
            records.putIfAbsent(record.getEventId(), record);
        }

        @Override
        public Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId) {
            return Optional.ofNullable(records.get(eventId));
        }

        @Override
        public int updateStatusProcessing(TradeEventConsumeRecordEntity record) {
            if (record.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_INIT
                    || record.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_RETRY) {
                record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_PROCESSING);
                return 1;
            }
            return 0;
        }

        @Override
        public int updateStatusConsumed(TradeEventConsumeRecordEntity record) {
            if (record.getConsumeStatus() != TradeEventConsumeRecordEntity.STATUS_PROCESSING) {
                return 0;
            }
            record.setConsumeCount(record.getConsumeCount() + 1);
            record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_CONSUMED);
            return 1;
        }

        @Override
        public int updateStatusRetry(TradeEventConsumeRecordEntity record) {
            if (record.getConsumeStatus() != TradeEventConsumeRecordEntity.STATUS_PROCESSING) {
                return 0;
            }
            record.setConsumeCount(record.getConsumeCount() + 1);
            record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_RETRY);
            return 1;
        }

        @Override
        public int updateStatusDeadLetter(TradeEventConsumeRecordEntity record) {
            if (record.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_INIT
                    || record.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_RETRY
                    || record.getConsumeStatus() == TradeEventConsumeRecordEntity.STATUS_PROCESSING) {
                record.setConsumeCount(record.getConsumeCount() + 1);
                record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_DEAD_LETTER);
                return 1;
            }
            return 0;
        }
    }
}















