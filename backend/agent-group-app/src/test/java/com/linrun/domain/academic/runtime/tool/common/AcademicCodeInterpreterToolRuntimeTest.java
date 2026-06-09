package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicCodeInterpreterToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteCodeThroughPortAndExposeStructuredOutput() {
        AcademicCodeInterpreterPort port = request ->
                new AcademicCodeInterpreterPort.AcademicCodeExecutionResult(
                        true,
                        0,
                        "sum=6",
                        "",
                        "计算完成",
                        request.code(),
                        "完成列表求和",
                        List.of(AcademicToolFileRef.builder()
                                .artifactId("A-CODE-1")
                                .fileName("result.csv")
                                .downloadUrl("/artifacts/A-CODE-1")
                                .build()));
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicCodeInterpreterToolRuntime.definition(),
                new AcademicCodeInterpreterToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.CODE_INTERPRETER)
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
        AcademicCodeInterpreterPort port = request ->
                new AcademicCodeInterpreterPort.AcademicCodeExecutionResult(
                        true,
                        0,
                        "ok",
                        "",
                        "ok",
                        request.code(),
                        "ok",
                        List.of());
        AcademicCodeInterpreterToolRuntime runtime = new AcademicCodeInterpreterToolRuntime(port);

        AcademicToolStructuredOutput output = runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.CODE_INTERPRETER)
                .arguments(Map.of(
                        "task", "write workspace note",
                        "permissionProfile", "workspace"))
                .build());

        Map<String, Object> metadata = output.getMetadata();
        assertEquals("workspace", metadata.get("permissionProfile"));
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AcademicCodeInterpreterToolRuntime runtime = new AcademicCodeInterpreterToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.CODE_INTERPRETER)
                        .arguments(Map.of("task", "run code"))
                        .build()));

        assertEquals("CODE_0001", exception.getCode());
    }

    @Test
    void shouldRejectUnsupportedPermissionProfile() {
        AcademicCodeInterpreterToolRuntime runtime = new AcademicCodeInterpreterToolRuntime(request ->
                new AcademicCodeInterpreterPort.AcademicCodeExecutionResult(
                        true, 0, "", "", "", "", "", List.of()));

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.CODE_INTERPRETER)
                        .arguments(Map.of(
                                "task", "run code",
                                "permissionProfile", "admin"))
                        .build()));

        assertEquals("CODE_0003", exception.getCode());
    }
}















