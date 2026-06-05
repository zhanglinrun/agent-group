package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeReranker;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.infrastructure.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
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
    private final KnowledgeReranker knowledgeReranker;
    private final boolean keywordFallbackEnabled;
    private final DynamicConfigService dynamicConfigService;

    @Autowired
    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                       KnowledgeKeywordService knowledgeKeywordService,
                                       KnowledgeVectorService knowledgeVectorService,
                                       KnowledgeReranker knowledgeReranker,
                                      @Value("${agent.group.vector.keyword-fallback-enabled:true}") boolean keywordFallbackEnabled,
                                      DynamicConfigService dynamicConfigService) {
        this.guideDataDao = guideDataDao;
        this.knowledgeKeywordService = knowledgeKeywordService;
        this.knowledgeVectorService = knowledgeVectorService;
        this.knowledgeReranker = knowledgeReranker == null ? KnowledgeReranker.noop() : knowledgeReranker;
        this.keywordFallbackEnabled = keywordFallbackEnabled;
        this.dynamicConfigService = dynamicConfigService;
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService,
                                      KnowledgeVectorService knowledgeVectorService,
                                      KnowledgeReranker knowledgeReranker,
                                      boolean keywordFallbackEnabled) {
        this(guideDataDao, knowledgeKeywordService, knowledgeVectorService, knowledgeReranker,
                keywordFallbackEnabled, null);
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService,
                                      KnowledgeVectorService knowledgeVectorService) {
        this(guideDataDao, knowledgeKeywordService, knowledgeVectorService, KnowledgeReranker.noop(), true);
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
            return knowledgeReranker.rerank(question,
                    rerank(question, keywords, vectorReferences, List.of(), safeLimit), safeLimit);
        }
        List<GuideReference> keywordReferences = AgentPOConverter.toGuideReferences(
                guideDataDao.queryReferences(keywords, safeLimit * 2));
        return knowledgeReranker.rerank(question,
                rerank(question, keywords, vectorReferences, keywordReferences, safeLimit), safeLimit);
    }

    @Override
    public List<GuideProduct> queryCandidateProducts(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return AgentPOConverter.toGuideProducts(
                        guideDataDao.queryCandidateProducts(knowledgeKeywordService.extractKeywords(question), safeLimit))
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<GuideProduct> queryRecommendProduct(String question) {
        GuideProduct product = AgentPOConverter.toEntity(guideDataDao.queryRecommendProduct(question));
        return normalizeProduct(product);
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        GuideProduct product = AgentPOConverter.toEntity(guideDataDao.queryProductByGoodsId(goodsId));
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
                    .flatMap(fragment -> expandVectorFragment(fragment, rank).stream())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<GuideReference> expandVectorFragment(KnowledgeFragment fragment, AtomicInteger rank) {
        GuideReference hit = activeVectorHit(fragment);
        if (hit == null) {
            return List.of();
        }
        if (!shouldExpandKnowledgeContext()) {
            return List.of(hit);
        }
        Map<String, GuideReference> expanded = new LinkedHashMap<>();
        if (StringUtils.hasText(fragment.getParentFragmentId())) {
            GuideReference parent = AgentPOConverter.toEntity(
                    guideDataDao.queryReferenceByFragmentId(fragment.getParentFragmentId()));
            appendExpanded(expanded, parent, rank);
        }
        appendExpanded(expanded, hit, rank);
        if (StringUtils.hasText(fragment.getBrotherGroupId())) {
            List<GuideReference> siblings = AgentPOConverter.toGuideReferences(
                    guideDataDao.querySiblingReferences(fragment.getBrotherGroupId(), 6));
            for (GuideReference sibling : siblings) {
                appendExpanded(expanded, sibling, rank);
            }
        }
        return expanded.isEmpty() ? List.of(hit) : new ArrayList<>(expanded.values());
    }

    private GuideReference activeVectorHit(KnowledgeFragment fragment) {
        if (fragment == null) {
            return null;
        }
        if (!StringUtils.hasText(fragment.getFragmentId())) {
            return toGuideReference(fragment, 0);
        }
        GuideReference reference = AgentPOConverter.toEntity(
                guideDataDao.queryReferenceByFragmentId(fragment.getFragmentId()));
        if (reference == null) {
            return null;
        }
        reference.setRank(0);
        return reference;
    }

    private void appendExpanded(Map<String, GuideReference> expanded,
                                GuideReference reference,
                                AtomicInteger rank) {
        if (reference == null) {
            return;
        }
        reference.setRank(rank.getAndIncrement());
        expanded.putIfAbsent(referenceKey(reference), reference);
    }

    private boolean shouldExpandKnowledgeContext() {
        return dynamicConfigService == null || dynamicConfigService.isKnowledgeContextExpansionOpen();
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
        if (containsAny(normalizedQuestion, "隔很久", "之前的价格", "报价")
                && containsAny(content, "报价凭证", "有效期", "过期", "重新校验", "活动")) {
            score += 30;
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
        reference.setParentFragmentId(fragment.getParentFragmentId());
        reference.setBrotherGroupId(fragment.getBrotherGroupId());
        reference.setBrotherIndex(fragment.getBrotherIndex());
        reference.setBrotherTotal(fragment.getBrotherTotal());
        reference.setChunkType(fragment.getChunkType());
        return reference;
    }

    private record RankedReference(GuideReference reference, int score) {
    }
}
