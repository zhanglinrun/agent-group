package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentFileToolPort {

    AgentFileToolResult upload(AgentFileUploadRequest request);

    AgentFileToolResult get(AgentFileGetRequest request);

    record AgentFileUploadRequest(String requestId,
                                     String fileName,
                                     String description,
                                     String content,
                                     String contentType,
                                     boolean internalFile) {
    }

    record AgentFileGetRequest(String requestId,
                                  String fileName,
                                  int maxContentChars) {
    }

    record AgentFileToolResult(boolean success,
                                  String command,
                                  String fileName,
                                  String content,
                                  String summary,
                                  List<AgentToolFileRef> fileRefs,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }
}















