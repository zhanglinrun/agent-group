package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicScriptRunnerToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunScriptThroughPortAndExposeStructuredOutput() {
        AtomicReference<AcademicScriptRunnerPort.AcademicScriptRunRequest> capturedRequest = new AtomicReference<>();
        AcademicScriptRunnerPort port = request -> {
            capturedRequest.set(request);
            return new AcademicScriptRunnerPort.AcademicScriptRunResult(
                    true,
                    0,
                    "ok",
                    "",
                    "script executed",
                    List.of(AcademicToolFileRef.builder()
                            .artifactId("A-SCRIPT-1")
                            .fileName("output.md")
                            .build()),
                    Map.of("sandbox", "remote"),
                    "");
        };
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicScriptRunnerToolRuntime.definition(),
                new AcademicScriptRunnerToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
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
        AcademicScriptRunnerToolRuntime runtime = new AcademicScriptRunnerToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of("skillName", "demo", "scriptName", "run"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0001", exception.getCode());
    }

    @Test
    void shouldRejectScriptPathTraversalBeforePortCall() {
        AtomicBoolean called = new AtomicBoolean(false);
        AcademicScriptRunnerToolRuntime runtime = new AcademicScriptRunnerToolRuntime(request -> {
            called.set(true);
            return null;
        });

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
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
        AcademicScriptRunnerToolRuntime runtime = new AcademicScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "scriptPath", "C:/Windows/System32/cmd.exe"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0005", exception.getCode());
    }

    @Test
    void shouldRejectInvalidRuntimeBeforePortCall() {
        AcademicScriptRunnerToolRuntime runtime = new AcademicScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "runtime", "cmd"))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0004", exception.getCode());
    }

    @Test
    void shouldRejectControlCharacterArgvBeforePortCall() {
        AcademicScriptRunnerToolRuntime runtime = new AcademicScriptRunnerToolRuntime(request -> null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                        .arguments(Map.of(
                                "skillName", "demo",
                                "scriptName", "run",
                                "argv", List.of("ok", "bad\narg")))
                        .build()));

        assertEquals("SCRIPT_RUNNER_0006", exception.getCode());
    }
}














