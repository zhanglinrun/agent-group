package com.linrun.domain.agent.knowledge.service.splitter;

import java.util.List;

public interface DocumentSplitStrategy {

    boolean supports(String content);

    List<String> split(String content);
}
