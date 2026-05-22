package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgeSearchToolService {

    private static final int DEFAULT_LIMIT = 3;

    private final GuideDataRepository guideDataRepository;

    public KnowledgeSearchToolService(GuideDataRepository guideDataRepository) {
        this.guideDataRepository = guideDataRepository;
    }

    public List<GuideReference> search(String question) {
        return search(question, DEFAULT_LIMIT);
    }

    public List<GuideReference> search(String question, int limit) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }
        return guideDataRepository.queryReferences(question, Math.max(1, limit));
    }
}
