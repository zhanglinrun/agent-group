package com.linrun.domain.agent.memory.adapter;

import com.linrun.domain.agent.memory.model.UserAgentMemory;

import java.util.List;
import java.util.Optional;

public interface UserAgentMemoryRepository {

    List<UserAgentMemory> queryByUser(String userId, boolean enabledOnly, int limit);

    Optional<UserAgentMemory> queryByType(String userId, String memoryType);

    void upsert(UserAgentMemory memory);

    int disable(String userId, String memoryType);

    int delete(String userId, String memoryType);
}
