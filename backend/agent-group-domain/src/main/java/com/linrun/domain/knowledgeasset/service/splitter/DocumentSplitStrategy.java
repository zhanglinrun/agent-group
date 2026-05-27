package com.linrun.domain.knowledgeasset.service.splitter;

import java.util.List;

public interface DocumentSplitStrategy {

    boolean supports(String content);

    List<String> split(String content);
}
