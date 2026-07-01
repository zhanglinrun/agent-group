package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFileToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldUploadFileThroughPortAndExposeFileRef() {
        AgentFileToolPort port = new AgentFileToolPort() {
            @Override
            public AgentFileToolResult upload(AgentFileUploadRequest request) {
                return new AgentFileToolResult(
                        true,
                        "upload",
                        request.fileName(),
                        "",
                        "file uploaded",
                        List.of(AgentToolFileRef.builder()
                                .artifactId("A-FILE-1")
                                .fileName(request.fileName())
                                .downloadUrl("/artifacts/A-FILE-1")
                                .build()),
                        Map.of("contentType", request.contentType()),
                        "");
            }

            @Override
            public AgentFileToolResult get(AgentFileGetRequest request) {
                throw new AssertionError("get should not be called");
            }
        };
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentFileToolRuntime.definition(), new AgentFileToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.FILE_TOOL)
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
        AgentFileToolRuntime runtime = new AgentFileToolRuntime(new NoopFileToolPort());

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.FILE_TOOL)
                        .arguments(Map.of("command", "delete", "fileName", "a.md"))
                        .build()));

        assertEquals("FILE_TOOL_0002", exception.getCode());
    }

    private static final class NoopFileToolPort implements AgentFileToolPort {
        @Override
        public AgentFileToolResult upload(AgentFileUploadRequest request) {
            return null;
        }

        @Override
        public AgentFileToolResult get(AgentFileGetRequest request) {
            return null;
        }
    }
}















