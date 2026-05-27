package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideQueryRoute;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.conversation.model.KnowledgeSearchResult;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeSearchToolService {

    private static final int DEFAULT_LIMIT = 3;

    private final GuideDataRepository guideDataRepository;
    private final GuideQueryRewriteService guideQueryRewriteService;
    private final GuideQueryRouterService guideQueryRouterService;

    public KnowledgeSearchToolService(GuideDataRepository guideDataRepository) {
        this(guideDataRepository, new GuideQueryRewriteService(), new GuideQueryRouterService());
    }

    @Autowired
    public KnowledgeSearchToolService(GuideDataRepository guideDataRepository,
                                      GuideQueryRewriteService guideQueryRewriteService,
                                      GuideQueryRouterService guideQueryRouterService) {
        this.guideDataRepository = guideDataRepository;
        this.guideQueryRewriteService = guideQueryRewriteService;
        this.guideQueryRouterService = guideQueryRouterService == null
                ? new GuideQueryRouterService()
                : guideQueryRouterService;
    }

    public List<GuideReference> search(String question) {
        return search(question, DEFAULT_LIMIT);
    }

    public List<GuideReference> search(String question, int limit) {
        return searchWithRoute(question, limit).getReferences();
    }

    public KnowledgeSearchResult searchWithRoute(String question, int limit) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        int safeLimit = Math.max(1, limit);
        GuideQueryRoute route = guideQueryRouterService.route(question);
        List<GuideReference> references = new ArrayList<>();
        for (String query : guideQueryRewriteService.rewrite(question)) {
            references.addAll(guideDataRepository.queryReferences(query, safeLimit));
        }
        return new KnowledgeSearchResult(route, guideQueryRewriteService.mergeAndRank(question, references, safeLimit));
    }

    public GuideQueryRoute route(String question) {
        return guideQueryRouterService.route(question);
    }
}
