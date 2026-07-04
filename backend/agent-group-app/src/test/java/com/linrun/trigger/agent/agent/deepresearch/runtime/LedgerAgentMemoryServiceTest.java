package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.domain.agent.memory.adapter.UserAgentMemoryRepository;
import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.domain.agent.memory.service.UserAgentMemoryService;
import com.linrun.trigger.config.AgentDeepRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerAgentMemoryServiceTest {

    @Test
    void loadsLongTermUserMemoryEvenWhenLedgerUnavailable() {
        UserAgentMemory memory = new UserAgentMemory();
        memory.setUserId("U1001");
        memory.setMemoryType("preference");
        memory.setContent("喜欢报告式回答，先结论后证据");
        memory.setEnabled(true);
        memory.setSource("manual");
        LedgerAgentMemoryService service = new LedgerAgentMemoryService(null,
                new UserAgentMemoryService(new SingleMemoryRepository(memory)));

        AgentMemorySnapshot snapshot = service.load("U1001", "S1001", "R1001", "REQ1001");

        assertTrue(snapshot.longTermEnabled());
        assertEquals(1, snapshot.longTerm().size());
        assertTrue(snapshot.longTerm().get(0).contains("preference: 喜欢报告式回答"));
    }

    @Test
    void skipsStyleMemoryForResearchQuestion() {
        UserAgentMemory style = new UserAgentMemory();
        style.setUserId("U1001");
        style.setMemoryType("output_style");
        style.setContent("偏好结构化报告");
        style.setEnabled(true);
        style.setSource("manual");

        AgentDeepRuntimeProperties properties = new AgentDeepRuntimeProperties();
        LedgerAgentMemoryService service = new LedgerAgentMemoryService(null,
                new UserAgentMemoryService(new SingleMemoryRepository(style)), properties);

        AgentMemorySnapshot snapshot = service.load(
                "U1001", "S1001", "R1001", "REQ1001", "半监督 AMC 论文综述 2024");

        assertTrue(snapshot.longTerm().isEmpty());
    }

    @Test
    void disabledLongTermMemoryIsNotLoaded() {
        UserAgentMemory memory = new UserAgentMemory();
        memory.setUserId("U1001");
        memory.setMemoryType("preference");
        memory.setContent("不要注入");
        memory.setEnabled(false);
        LedgerAgentMemoryService service = new LedgerAgentMemoryService(null,
                new UserAgentMemoryService(new SingleMemoryRepository(memory)));

        AgentMemorySnapshot snapshot = service.load("U1001", "S1001", "R1001", "REQ1001");

        assertTrue(snapshot.longTerm().isEmpty());
        assertTrue(!snapshot.longTermEnabled());
    }

    private static class SingleMemoryRepository implements UserAgentMemoryRepository {
        private final UserAgentMemory memory;

        private SingleMemoryRepository(UserAgentMemory memory) {
            this.memory = memory;
        }

        @Override
        public List<UserAgentMemory> queryByUser(String userId, boolean enabledOnly, int limit) {
            if (memory == null || !userId.equals(memory.getUserId())) {
                return List.of();
            }
            if (enabledOnly && !Boolean.TRUE.equals(memory.getEnabled())) {
                return List.of();
            }
            return List.of(memory);
        }

        @Override
        public Optional<UserAgentMemory> queryByType(String userId, String memoryType) {
            return Optional.empty();
        }

        @Override
        public void upsert(UserAgentMemory memory) {
        }

        @Override
        public int disable(String userId, String memoryType) {
            return 0;
        }

        @Override
        public int delete(String userId, String memoryType) {
            return 0;
        }
    }
}
