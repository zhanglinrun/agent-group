package com.linrun.trigger.agent.agent.deepresearch.runtime;

public interface AgentMemoryService {

    AgentMemorySnapshot load(String userId,
                             String sessionId,
                             String runId,
                             String currentRequestId);

    AgentMemorySnapshot load(String userId,
                             String sessionId,
                             String runId,
                             String currentRequestId,
                             String question);
}
