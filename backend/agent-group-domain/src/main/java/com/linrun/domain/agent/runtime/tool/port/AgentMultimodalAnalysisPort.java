package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentMultimodalAnalysisPort {

    AgentMultimodalAnalysisResult analyze(AgentMultimodalAnalysisRequest request);

    record AgentMultimodalAnalysisRequest(String task,
                                             String text,
                                             List<String> imageUrls,
                                             List<String> fileUrls) {
    }

    record AgentMultimodalAnalysisResult(boolean success,
                                            String summary,
                                            String content,
                                            Map<String, Object> metadata,
                                            List<AgentToolFileRef> fileRefs,
                                            String errorMessage) {
    }
}















