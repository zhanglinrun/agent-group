package com.linrun.domain.trade.service;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEventConsumeAdminServiceTest {

    @Test
    void replayDeadLetterResetsStatusAndRepublishes() {
        InMemoryConsumeRecordRepository repository = new InMemoryConsumeRecordRepository();
        TradeEventConsumeRecordEntity dead = new TradeEventConsumeRecordEntity();
        dead.setEventId("EVT001");
        dead.setOrderId("O10001");
        dead.setBizType("GROUP");
        dead.setBizId("B10001");
        dead.setEventType("TEAM_SUCCESS");
        dead.setRoutingKey("trade.event.group.team_success");
        dead.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_DEAD_LETTER);
        repository.save(dead);

        AtomicReference<TradeEventMessageEntity> published = new AtomicReference<>();
        TradeEventConsumeAdminService service = new TradeEventConsumeAdminService(
                repository,
                message -> published.set(message));

        assertTrue(service.replayDeadLetter("EVT001"));
        assertEquals(TradeEventConsumeRecordEntity.STATUS_INIT, repository.records.get(0).getConsumeStatus());
        assertEquals(0, repository.records.get(0).getConsumeCount());
        assertEquals("EVT001", published.get().getFlowId());
    }

    @Test
    void replayDeadLetterRejectsNonDeadStatus() {
        InMemoryConsumeRecordRepository repository = new InMemoryConsumeRecordRepository();
        TradeEventConsumeRecordEntity record = new TradeEventConsumeRecordEntity();
        record.setEventId("EVT002");
        record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_CONSUMED);
        repository.save(record);

        TradeEventConsumeAdminService service = new TradeEventConsumeAdminService(repository, TradeEventPublisher.noop());
        assertThrows(AppException.class, () -> service.replayDeadLetter("EVT002"));
    }

    private static class InMemoryConsumeRecordRepository implements TradeEventConsumeRecordRepository {
        private final List<TradeEventConsumeRecordEntity> records = new ArrayList<>();

        @Override
        public void save(TradeEventConsumeRecordEntity record) {
            records.removeIf(item -> item.getEventId().equals(record.getEventId()));
            records.add(record);
        }

        @Override
        public Optional<TradeEventConsumeRecordEntity> queryByEventId(String eventId) {
            return records.stream().filter(item -> eventId.equals(item.getEventId())).findFirst();
        }

        @Override
        public List<TradeEventConsumeRecordEntity> queryByStatus(int consumeStatus, int limit) {
            return records.stream()
                    .filter(item -> item.getConsumeStatus() != null && item.getConsumeStatus() == consumeStatus)
                    .limit(limit)
                    .toList();
        }

        @Override
        public int resetStatusForReplay(String eventId) {
            return queryByEventId(eventId).map(record -> {
                record.setConsumeStatus(TradeEventConsumeRecordEntity.STATUS_INIT);
                record.setConsumeCount(0);
                record.setLastError(null);
                return 1;
            }).orElse(0);
        }

        @Override
        public int updateStatusProcessing(TradeEventConsumeRecordEntity record) {
            return 0;
        }

        @Override
        public int updateStatusConsumed(TradeEventConsumeRecordEntity record) {
            return 0;
        }

        @Override
        public int updateStatusRetry(TradeEventConsumeRecordEntity record) {
            return 0;
        }

        @Override
        public int updateStatusDeadLetter(TradeEventConsumeRecordEntity record) {
            return 0;
        }
    }
}
