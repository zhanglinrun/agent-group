package com.linrun.infrastructure.conversation.repository;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.domain.knowledgeasset.service.KnowledgeKeywordService;
import com.linrun.domain.knowledgeasset.service.KnowledgeVectorService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class MyBatisGuideDataRepository implements GuideDataRepository {

    private final IGuideDataDao guideDataDao;
    private final KnowledgeKeywordService knowledgeKeywordService;
    private final KnowledgeVectorService knowledgeVectorService;
    private final boolean keywordFallbackEnabled;

    @Autowired
    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                       KnowledgeKeywordService knowledgeKeywordService,
                                       KnowledgeVectorService knowledgeVectorService,
                                      @Value("${agent.group.vector.keyword-fallback-enabled:true}") boolean keywordFallbackEnabled) {
        this.guideDataDao = guideDataDao;
        this.knowledgeKeywordService = knowledgeKeywordService;
        this.knowledgeVectorService = knowledgeVectorService;
        this.keywordFallbackEnabled = keywordFallbackEnabled;
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService,
                                      KnowledgeVectorService knowledgeVectorService) {
        this(guideDataDao, knowledgeKeywordService, knowledgeVectorService, true);
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao, KnowledgeKeywordService knowledgeKeywordService) {
        this(guideDataDao, knowledgeKeywordService, null);
    }

    @Override
    public List<GuideReference> queryReferences(String question, int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> keywords = knowledgeKeywordService.extractKeywords(question);
        List<GuideReference> vectorReferences = queryVectorReferences(question, safeLimit * 2);
        if (!keywordFallbackEnabled) {
            return rerank(question, keywords, vectorReferences, List.of(), safeLimit);
        }
        List<GuideReference> keywordReferences = guideDataDao.queryReferences(keywords, safeLimit * 2);
        return rerank(question, keywords, vectorReferences, keywordReferences, safeLimit);
    }

    @Override
    public List<GuideProduct> queryCandidateProducts(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return guideDataDao.queryCandidateProducts(knowledgeKeywordService.extractKeywords(question), safeLimit)
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<GuideProduct> queryRecommendProduct(String question) {
        GuideProduct product = guideDataDao.queryRecommendProduct(question);
        return normalizeProduct(product);
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        GuideProduct product = guideDataDao.queryProductByGoodsId(goodsId);
        return normalizeProduct(product);
    }

    private Optional<GuideProduct> normalizeProduct(GuideProduct product) {
        if (product == null) {
            return Optional.empty();
        }
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null || product.getRemainingSeconds() <= 0) {
            product.setRemainingSeconds((int) Duration.ofMinutes(30).toSeconds());
        }
        return Optional.of(product);
    }

    private List<GuideReference> queryVectorReferences(String question, int limit) {
        if (knowledgeVectorService == null) {
            return List.of();
        }
        try {
            AtomicInteger rank = new AtomicInteger(1);
            return knowledgeVectorService.searchSimilar(question, limit).stream()
                    .map(fragment -> toGuideReference(fragment, rank.getAndIncrement()))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<GuideReference> rerank(String question,
                                        List<String> keywords,
                                        List<GuideReference> vectorReferences,
                                        List<GuideReference> keywordReferences,
                                        int limit) {
        Map<String, RankedReference> rankedReferences = new LinkedHashMap<>();
        addRankedReferences(rankedReferences, question, keywords, vectorReferences, 100, 8);
        addRankedReferences(rankedReferences, question, keywords, keywordReferences, 80, 6);
        AtomicInteger rank = new AtomicInteger(1);
        return rankedReferences.values().stream()
                .sorted(Comparator.comparingInt(RankedReference::score).reversed())
                .limit(limit)
                .map(RankedReference::reference)
                .peek(reference -> reference.setRank(rank.getAndIncrement()))
                .toList();
    }

    private void addRankedReferences(Map<String, RankedReference> rankedReferences,
                                     String question,
                                     List<String> keywords,
                                     List<GuideReference> references,
                                     int baseScore,
                                     int rankPenalty) {
        if (references == null || references.isEmpty()) {
            return;
        }
        for (int i = 0; i < references.size(); i++) {
            GuideReference reference = references.get(i);
            if (reference == null) {
                continue;
            }
            String key = referenceKey(reference);
            int score = baseScore - (i * rankPenalty) + relevanceScore(question, keywords, reference);
            rankedReferences.merge(key, new RankedReference(reference, score),
                    (left, right) -> left.score() >= right.score() ? left : right);
        }
    }

    private int relevanceScore(String question, List<String> keywords, GuideReference reference) {
        String content = safe(reference.getContent()).toLowerCase();
        String documentType = safe(reference.getDocumentType()).toLowerCase();
        String goodsId = safe(reference.getGoodsId()).toLowerCase();
        String normalizedQuestion = safe(question).toLowerCase();
        int score = 0;
        for (String keyword : keywords == null ? List.<String>of() : keywords) {
            String normalizedKeyword = safe(keyword).toLowerCase();
            if (normalizedKeyword.isBlank()) {
                continue;
            }
            if (content.contains(normalizedKeyword)) {
                score += 12;
            }
            if (documentType.contains(normalizedKeyword)) {
                score += 6;
            }
        }
        if (!goodsId.isBlank() && normalizedQuestion.contains(goodsId)) {
            score += 20;
        }
        if (containsAny(normalizedQuestion, "退款", "退货", "售后") && containsAny(documentType + content, "售后", "退款", "退货")) {
            score += 15;
        }
        if (containsAny(normalizedQuestion, "拼团", "成团", "团购", "优惠") && containsAny(documentType + content, "拼团", "成团", "优惠")) {
            score += 15;
        }
        return score;
    }

    private String referenceKey(GuideReference reference) {
        if (reference == null) {
            return "";
        }
        if (reference.getFragmentId() != null && !reference.getFragmentId().isBlank()) {
            return reference.getFragmentId();
        }
        return safe(reference.getDocumentId()) + "|" + safe(reference.getContent());
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private GuideReference toGuideReference(KnowledgeFragment fragment, int rank) {
        GuideReference reference = new GuideReference();
        reference.setFragmentId(fragment.getFragmentId());
        reference.setDocumentId(fragment.getDocumentId());
        reference.setGoodsId(fragment.getGoodsId());
        reference.setDocumentType(fragment.getDocumentType());
        reference.setKnowledgeVersion(fragment.getKnowledgeVersion());
        reference.setContent(fragment.getContent());
        reference.setRank(rank);
        return reference;
    }

    private record RankedReference(GuideReference reference, int score) {
    }
}
