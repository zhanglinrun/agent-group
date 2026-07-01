package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentDeepSearchPort {

    AgentDeepSearchResult search(AgentDeepSearchRequest request);

    record AgentDeepSearchRequest(String query,
                                     int maxResults,
                                     boolean stream,
                                     List<String> sourceTypes,
                                     Map<String, Object> options) {
    }

    record AgentDeepSearchResult(boolean success,
                                    String query,
                                    String answer,
                                    String answerSummary,
                                    List<String> subQueries,
                                    List<AgentDeepSearchDocument> documents,
                                    List<AgentToolFileRef> fileRefs,
                                    Map<String, Object> metadata,
                                    String errorMessage) {
    }

    record AgentDeepSearchDocument(String title,
                                      String url,
                                      String content,
                                      String source) {
    }
}















