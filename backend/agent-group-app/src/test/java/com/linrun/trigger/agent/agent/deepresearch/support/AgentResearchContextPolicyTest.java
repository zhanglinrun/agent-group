package com.linrun.trigger.agent.agent.deepresearch.support;

import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.domain.agent.memory.model.UserAgentMemorySources;
import com.linrun.trigger.config.AgentDeepRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResearchContextPolicyTest {

    @Test
    void detectsResearchQuestion() {
        assertTrue(AgentResearchContextPolicy.isResearchQuestion(
                "半监督自动调制识别近两年的发展历程", new AgentDeepRuntimeProperties().getResearchKeywords()));
    }

    @Test
    void skipsStyleMemoryForResearchQuestion() {
        UserAgentMemory memory = new UserAgentMemory();
        memory.setEnabled(true);
        memory.setSource(UserAgentMemorySources.MANUAL);
        memory.setMemoryType("output_style");
        memory.setContent("偏好结构化报告");

        assertFalse(AgentResearchContextPolicy.shouldInjectMemory(
                memory, "AMC 论文综述 2024", new AgentDeepRuntimeProperties()));
    }

    @Test
    void keepsPreferenceMemoryForResearchQuestion() {
        UserAgentMemory memory = new UserAgentMemory();
        memory.setEnabled(true);
        memory.setSource(UserAgentMemorySources.MANUAL);
        memory.setMemoryType("preference");
        memory.setContent("默认简短回答");

        assertTrue(AgentResearchContextPolicy.shouldInjectMemory(
                memory, "AMC 论文综述 2024", new AgentDeepRuntimeProperties()));
    }
}
