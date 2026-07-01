package com.linrun.domain.agent.runtime.tool.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolArgumentsTest {

    @Test
    void shouldNormalizeTextAndDefaults() {
        assertEquals("", AgentToolArguments.text(null));
        assertEquals("query", AgentToolArguments.text("  query  "));
        assertEquals("fallback", AgentToolArguments.defaultText("  ", "fallback"));
        assertEquals("first", AgentToolArguments.firstPresent("", "  first  ", "second"));
    }

    @Test
    void shouldParseBooleansAndIntegersWithFallback() {
        assertTrue(AgentToolArguments.bool(true, false));
        assertTrue(AgentToolArguments.bool("true", false));
        assertTrue(AgentToolArguments.bool("", true));
        assertEquals(7, AgentToolArguments.integer(7L, 1));
        assertEquals(8, AgentToolArguments.integer("8", 1));
        assertEquals(1, AgentToolArguments.integer("bad", 1));
    }

    @Test
    void shouldFilterStringLists() {
        assertEquals(
                List.of("alpha", "beta"),
                AgentToolArguments.stringList(Arrays.asList(" alpha ", "", null, "beta")));
        assertEquals(List.of(), AgentToolArguments.stringList("alpha"));
    }

    @Test
    void shouldCopyMapArguments() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("k1", "v1");
        source.put(100, "ignored");

        Map<String, Object> copied = AgentToolArguments.objectMap(source);
        source.put("k1", "changed");

        assertEquals(Map.of("k1", "v1"), copied);
        assertEquals(
                List.of(Map.of("k2", "v2"), Map.of()),
                AgentToolArguments.mapList(List.of(Map.of("k2", "v2"), Map.of(1, "ignored"), Map.of(), "skip")));
    }
}
