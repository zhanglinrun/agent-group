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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicScriptRunnerToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunScriptThroughPortAndExposeStructuredOutput() {
        AcademicScriptRunnerPort port = request -> new AcademicScriptRunnerPort.AcademicScriptRunResult(
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
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicScriptRunnerToolRuntime.definition(),
                new AcademicScriptRunnerToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                .arguments(Map.of(
                        "skillName", "demo-skill",
                        "scriptName", "summarize",
                        "runtime", "python",
                        "arguments", Map.of("orderId", "O1001")))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("remote", metadata.get("sandbox"));
        assertEquals(true, metadata.get("success"));
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
}















