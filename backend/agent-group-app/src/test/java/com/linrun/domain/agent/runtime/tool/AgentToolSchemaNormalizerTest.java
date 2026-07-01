package com.linrun.domain.agent.runtime.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolSchemaNormalizerTest {

    @Test
    void shouldCompleteEmptySchemaAsObjectSchema() {
        Map<String, Object> normalized = AgentToolSchemaNormalizer.normalize(null);

        assertEquals("object", normalized.get("type"));
        assertInstanceOf(Map.class, normalized.get("properties"));
        assertInstanceOf(List.class, normalized.get("required"));
        assertTrue(((Map<?, ?>) normalized.get("properties")).isEmpty());
        assertTrue(((List<?>) normalized.get("required")).isEmpty());
    }

    @Test
    void shouldCompleteObjectSchemaWithMissingPropertiesAndRequired() {
        Map<String, Object> normalized = AgentToolSchemaNormalizer.normalize(Map.of("type", "object"));

        assertEquals("object", normalized.get("type"));
        assertInstanceOf(Map.class, normalized.get("properties"));
        assertInstanceOf(List.class, normalized.get("required"));
    }

    @Test
    void shouldRepairInvalidPropertiesAndRemoveUnsupportedFields() {
        Map<String, Object> normalized = AgentToolSchemaNormalizer.normalize(Map.of(
                "type", "object",
                "$schema", "https://json-schema.org/draft/2020-12/schema",
                "additionalProperties", false,
                "properties", "bad",
                "required", "bad"));

        assertEquals("object", normalized.get("type"));
        assertFalse(normalized.containsKey("$schema"));
        assertFalse(normalized.containsKey("additionalProperties"));
        assertInstanceOf(Map.class, normalized.get("properties"));
        assertInstanceOf(List.class, normalized.get("required"));
    }

    @Test
    void shouldLetToolDefinitionDeriveRequiredArgumentsFromNormalizedSchema() {
        AgentToolDefinition definition = AgentToolDefinition.builder("mcp_chart")
                .description("render chart")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")))
                .build();

        assertEquals(List.of("query"), definition.getRequiredArguments());
        assertEquals("object", definition.getInputSchema().get("type"));
    }
}















