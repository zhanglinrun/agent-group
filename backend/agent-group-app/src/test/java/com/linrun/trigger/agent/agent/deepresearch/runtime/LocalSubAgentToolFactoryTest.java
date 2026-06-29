package com.linrun.trigger.agent.agent.deepresearch.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSubAgentToolFactoryTest {

    @Test
    void shouldRegisterLocalSubAgentsAsTools() {
        ToolCallback[] tools = LocalSubAgentToolFactory.create();
        Set<String> names = Arrays.stream(tools)
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(2, tools.length);
        assertTrue(names.contains("file_reader_agent"));
        assertTrue(names.contains("report_reviewer_agent"));
    }
}
