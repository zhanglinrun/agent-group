package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicFileToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldUploadFileThroughPortAndExposeFileRef() {
        AcademicFileToolPort port = new AcademicFileToolPort() {
            @Override
            public AcademicFileToolResult upload(AcademicFileUploadRequest request) {
                return new AcademicFileToolResult(
                        true,
                        "upload",
                        request.fileName(),
                        "",
                        "file uploaded",
                        List.of(AcademicToolFileRef.builder()
                                .artifactId("A-FILE-1")
                                .fileName(request.fileName())
                                .downloadUrl("/artifacts/A-FILE-1")
                                .build()),
                        Map.of("contentType", request.contentType()),
                        "");
            }

            @Override
            public AcademicFileToolResult get(AcademicFileGetRequest request) {
                throw new AssertionError("get should not be called");
            }
        };
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicFileToolRuntime.definition(), new AcademicFileToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.FILE_TOOL)
                .arguments(Map.of(
                        "command", "upload",
                        "fileName", "quota-report.md",
                        "content", "# report"))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("upload", metadata.get("command"));
        assertEquals(List.of("A-FILE-1"), result.getArtifactIds());
    }

    @Test
    void shouldRejectUnsupportedCommand() {
        AcademicFileToolRuntime runtime = new AcademicFileToolRuntime(new NoopFileToolPort());

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.FILE_TOOL)
                        .arguments(Map.of("command", "delete", "fileName", "a.md"))
                        .build()));

        assertEquals("FILE_TOOL_0002", exception.getCode());
    }

    private static final class NoopFileToolPort implements AcademicFileToolPort {
        @Override
        public AcademicFileToolResult upload(AcademicFileUploadRequest request) {
            return null;
        }

        @Override
        public AcademicFileToolResult get(AcademicFileGetRequest request) {
            return null;
        }
    }
}















