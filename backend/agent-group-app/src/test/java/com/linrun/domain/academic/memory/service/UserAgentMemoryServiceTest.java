package com.linrun.domain.academic.memory.service;

import com.linrun.domain.academic.memory.adapter.UserAgentMemoryRepository;
import com.linrun.domain.academic.memory.model.UserAgentMemory;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAgentMemoryServiceTest {

    @Test
    void savesAndDisablesUserScopedMemory() {
        FakeRepository repository = new FakeRepository();
        UserAgentMemoryService service = new UserAgentMemoryService(repository);

        UserAgentMemory saved = service.save(" U1001 ", "Preference 中文", " 喜欢报告式回答 ", true);
        assertTrue(saved.getMemoryType().startsWith("preference"));
        assertEquals("喜欢报告式回答", saved.getContent());
        assertTrue(saved.getEnabled());

        assertEquals(1, service.queryEnabled("U1001", 10).size());
        assertTrue(service.disable("U1001", "Preference 中文"));
        assertTrue(service.queryEnabled("U1001", 10).isEmpty());
        assertEquals(1, service.query("U1001", 10).size());
    }

    @Test
    void rejectsBlankMemoryContent() {
        UserAgentMemoryService service = new UserAgentMemoryService(new FakeRepository());

        assertThrows(AppException.class, () -> service.save("U1001", "preference", " ", true));
    }

    private static class FakeRepository implements UserAgentMemoryRepository {
        private final Map<String, UserAgentMemory> memories = new LinkedHashMap<>();

        @Override
        public List<UserAgentMemory> queryByUser(String userId, boolean enabledOnly, int limit) {
            return memories.values().stream()
                    .filter(memory -> userId.equals(memory.getUserId()))
                    .filter(memory -> !enabledOnly || Boolean.TRUE.equals(memory.getEnabled()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<UserAgentMemory> queryByType(String userId, String memoryType) {
            return Optional.ofNullable(memories.get(key(userId, memoryType)));
        }

        @Override
        public void upsert(UserAgentMemory memory) {
            memories.put(key(memory.getUserId(), memory.getMemoryType()), memory);
        }

        @Override
        public int disable(String userId, String memoryType) {
            UserAgentMemory memory = memories.get(key(userId, memoryType));
            if (memory == null) {
                return 0;
            }
            memory.setEnabled(false);
            return 1;
        }

        private String key(String userId, String memoryType) {
            return userId + ":" + memoryType;
        }
    }
}
