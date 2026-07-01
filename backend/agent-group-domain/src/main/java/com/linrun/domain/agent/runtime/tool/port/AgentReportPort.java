package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentReportPort {

    AgentReportResult generate(AgentReportRequest request);

    record AgentReportRequest(String requestId,
                                 String task,
                                 String title,
                                 String summary,
                                 List<Map<String, Object>> sections,
                                 List<String> evidence,
                                 List<String> fileNames,
                                 String fileName,
                                 String fileType,
                                 String templateType,
                                 boolean stream) {
    }

    record AgentReportResult(boolean success,
                                String content,
                                String summary,
                                List<AgentToolFileRef> fileRefs,
                                Map<String, Object> metadata,
                                String errorMessage) {
    }
}















