package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCodeInterpreterToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteCodeThroughPortAndExposeStructuredOutput() {
        AgentCodeInterpreterPort port = request ->
                new AgentCodeInterpreterPort.AgentCodeExecutionResult(
                        true,
                        0,
                        "sum=6",
                        "",
                        "计算完成",
                        request.code(),
                        "完成列表求和",
                        List.of(AgentToolFileRef.builder()
                                .artifactId("A-CODE-1")
                                .fileName("result.csv")
                                .downloadUrl("/artifacts/A-CODE-1")
                                .build()));
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentCodeInterpreterToolRuntime.definition(),
                new AgentCodeInterpreterToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.CODE_INTERPRETER)
                .arguments(Map.of(
                        "task", "计算销量总和",
                        "language", "python",
                        "code", "sum([1,2,3])"))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("计算销量总和", result.getResult().get("title"));
        assertEquals("analysis", metadata.get("permissionProfile"));
        assertEquals("sum=6", metadata.get("stdout"));
        assertEquals(List.of("A-CODE-1"), result.getArtifactIds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptWorkspacePermissionProfile() {
        AgentCodeInterpreterPort port = request ->
                new AgentCodeInterpreterPort.AgentCodeExecutionResult(
                        true,
                        0,
                        "ok",
                        "",
                        "ok",
                        request.code(),
                        "ok",
                        List.of());
        AgentCodeInterpreterToolRuntime runtime = new AgentCodeInterpreterToolRuntime(port);

        AgentToolStructuredOutput output = runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.CODE_INTERPRETER)
                .arguments(Map.of(
                        "task", "write workspace note",
                        "permissionProfile", "workspace"))
                .build());

        Map<String, Object> metadata = output.getMetadata();
        assertEquals("workspace", metadata.get("permissionProfile"));
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AgentCodeInterpreterToolRuntime runtime = new AgentCodeInterpreterToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.CODE_INTERPRETER)
                        .arguments(Map.of("task", "run code"))
                        .build()));

        assertEquals("CODE_0001", exception.getCode());
    }

    @Test
    void shouldRejectUnsupportedPermissionProfile() {
        AgentCodeInterpreterToolRuntime runtime = new AgentCodeInterpreterToolRuntime(request ->
                new AgentCodeInterpreterPort.AgentCodeExecutionResult(
                        true, 0, "", "", "", "", "", List.of()));

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.CODE_INTERPRETER)
                        .arguments(Map.of(
                                "task", "run code",
                                "permissionProfile", "admin"))
                        .build()));

        assertEquals("CODE_0003", exception.getCode());
    }
}















