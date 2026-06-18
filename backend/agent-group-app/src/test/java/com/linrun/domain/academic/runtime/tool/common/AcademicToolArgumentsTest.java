package com.linrun.domain.academic.runtime.tool.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicToolArgumentsTest {

    @Test
    void shouldNormalizeTextAndDefaults() {
        assertEquals("", AcademicToolArguments.text(null));
        assertEquals("query", AcademicToolArguments.text("  query  "));
        assertEquals("fallback", AcademicToolArguments.defaultText("  ", "fallback"));
        assertEquals("first", AcademicToolArguments.firstPresent("", "  first  ", "second"));
    }

    @Test
    void shouldParseBooleansAndIntegersWithFallback() {
        assertTrue(AcademicToolArguments.bool(true, false));
        assertTrue(AcademicToolArguments.bool("true", false));
        assertTrue(AcademicToolArguments.bool("", true));
        assertEquals(7, AcademicToolArguments.integer(7L, 1));
        assertEquals(8, AcademicToolArguments.integer("8", 1));
        assertEquals(1, AcademicToolArguments.integer("bad", 1));
    }

    @Test
    void shouldFilterStringLists() {
        assertEquals(
                List.of("alpha", "beta"),
                AcademicToolArguments.stringList(Arrays.asList(" alpha ", "", null, "beta")));
        assertEquals(List.of(), AcademicToolArguments.stringList("alpha"));
    }

    @Test
    void shouldCopyMapArguments() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("k1", "v1");
        source.put(100, "ignored");

        Map<String, Object> copied = AcademicToolArguments.objectMap(source);
        source.put("k1", "changed");

        assertEquals(Map.of("k1", "v1"), copied);
        assertEquals(
                List.of(Map.of("k2", "v2"), Map.of()),
                AcademicToolArguments.mapList(List.of(Map.of("k2", "v2"), Map.of(1, "ignored"), Map.of(), "skip")));
    }
}
