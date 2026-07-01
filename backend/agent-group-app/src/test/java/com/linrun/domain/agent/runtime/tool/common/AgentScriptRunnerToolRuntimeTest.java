package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentScriptRunnerPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScriptRunnerToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunScriptThroughPortAndExposeStructuredOutput() {
        AtomicReference<AgentScriptRunnerPort.AgentScriptRunRequest> capturedRequest = new AtomicReference<>();
        AgentScriptRunnerPort port = request -> {
            capturedRequest.set(request);
            return new AgentScriptRunnerPort.AgentScriptRunResult(
                    true,
                    0,
                    "ok",
                    "",
                    "script executed",
                    List.of(AgentToolFileRef.builder()
                            .artifactId("A-SCRIPT-1")
                            .fileName("output.md")
                            .build()),
                    Map.of("sandbox", "remote"),
                    "");
        };
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentScriptRunnerToolRuntime.definition(),
                new AgentScriptRunnerToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                .arguments(Map.of(
                        "skillName", "demo-skill",
                        "scriptName", "summarize",
                        "scriptPath", "scripts/../summarize.py",
                        "runtime", "PYTHON",
                        "timeoutSeconds", 999,
                        "arguments", Map.of("orderId", "O1001")))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("remote", metadata.get("sandbox"));
        assertEquals(true, metadata.get("success"));
        assertEquals("python", capturedRequest.get().runtime());
        assertEquals("summarize.py", capturedRequest.get().scriptPath());
        assertEquals(300, capturedRequest.get().timeoutSeconds());
        assertEquals(List.of("A-SCRIPT-1"), result.getArtifactIds());
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AgentScriptRunnerToolRuntime runtime = new AgentScriptRunnerToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of("skillName", "demo", "scriptName", "run"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0001", exception.getCode());
    }

    @Test
    void shouldRejectScriptPathTraversalBeforePortCall() {
        AtomicBoolean called = new AtomicBoolean(false);
        AgentScriptRunnerToolRuntime runtime = new AgentScriptRunnerToolRuntime(request -> {
            called.set(true);
            return null;
        });

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "scriptPath", "../run.py"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0005", exception.getCode());
        assertEquals(false, called.get());
    }

    @Test
    void shouldRejectAbsoluteScriptPathBeforePortCall() {
        AgentScriptRunnerToolRuntime runtime = new AgentScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "scriptPath", "C:/Windows/System32/cmd.exe"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0005", exception.getCode());
    }

    @Test
    void shouldRejectInvalidRuntimeBeforePortCall() {
        AgentScriptRunnerToolRuntime runtime = new AgentScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "runtime", "cmd"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0004", exception.getCode());
    }

    @Test
    void shouldRejectControlCharacterArgvBeforePortCall() {
        AgentScriptRunnerToolRuntime runtime = new AgentScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "argv", List.of("ok", "bad\narg")))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0006", exception.getCode());
    }
}














