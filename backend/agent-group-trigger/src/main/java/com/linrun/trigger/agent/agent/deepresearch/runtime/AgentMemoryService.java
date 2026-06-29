package com.linrun.trigger.agent.agent.deepresearch.runtime;

public interface AgentMemoryService {

    AgentMemorySnapshot load(String tenantId,
                             String userId,
                             String sessionId,
                             String runId,
                             String currentRequestId,
                             boolean longTermEnabled);
}
