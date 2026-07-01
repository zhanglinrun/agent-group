package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentWebFetchPort {

    AgentWebFetchResult fetch(AgentWebFetchRequest request);

    record AgentWebFetchRequest(String requestId,
                                   String url,
                                   int timeoutSeconds,
                                   int maxContentChars) {
    }

    record AgentWebFetchResult(boolean success,
                                  String title,
                                  String finalUrl,
                                  String content,
                                  String summary,
                                  List<AgentToolFileRef> fileRefs,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }
}















