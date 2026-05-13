package com.linrun.domain.evaluate.service;

import com.linrun.domain.evaluate.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.evaluate.model.GuideEvaluationCase;
import com.linrun.domain.evaluate.model.GuideEvaluationItemResult;
import com.linrun.domain.evaluate.model.GuideEvaluationReport;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.service.GuideDecisionService;
import com.linrun.domain.guide.service.GuideRagAnswerService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class GuideEvaluationService {

    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GuideEvaluationCaseRepository guideEvaluationCaseRepository;
    private final GuideDecisionService guideDecisionService;
    private final GuideRagAnswerService guideRagAnswerService;

    public GuideEvaluationService(GuideEvaluationCaseRepository guideEvaluationCaseRepository,
                                  GuideDecisionService guideDecisionService,
                                  GuideRagAnswerService guideRagAnswerService) {
        this.guideEvaluationCaseRepository = guideEvaluationCaseRepository;
        this.guideDecisionService = guideDecisionService;
        this.guideRagAnswerService = guideRagAnswerService;
    }

    public GuideEvaluationReport runBatch() {
        List<GuideEvaluationCase> cases = guideEvaluationCaseRepository.queryEnabledCases();
        List<GuideEvaluationItemResult> items = cases.stream()
                .map(this::evaluateOne)
                .toList();

        GuideEvaluationReport report = new GuideEvaluationReport();
        report.setBatchNo("EVAL" + LocalDateTime.now().format(BATCH_FORMATTER));
        report.setPromptVersion("guide-v1.0/self-check-v1.0");
        report.setKnowledgeVersion("v1");
        report.setTotalCount(items.size());
        report.setRetrievalHitRate(rate(items, GuideEvaluationItemResult::isReferencePassed));
        report.setAnswerAccuracyRate(rate(items, GuideEvaluationItemResult::isAnswerPassed));
        report.setRecommendationReasonableRate(rate(items, GuideEvaluationItemResult::isRecommendationPassed));
        report.setContextConsistencyRate(rate(items, GuideEvaluationItemResult::isContextPassed));
        report.setItems(items);
        return report;
    }

    private GuideEvaluationItemResult evaluateOne(GuideEvaluationCase evaluationCase) {
        GuideEvaluationItemResult item = baseItem(evaluationCase);
        try {
            GuideDecisionResult decisionResult = guideDecisionService.decide(evaluationCase.getQuestion());
            List<String> answerSegments = guideRagAnswerService.answer(evaluationCase.getQuestion(), decisionResult);
            String referenceText = decisionResult.getReferences().stream()
                    .map(GuideReference::getContent)
                    .collect(Collectors.joining("\n"));
            String answerText = String.join("\n", answerSegments);
            GuideProduct product = decisionResult.getProduct();

            item.setActualGoodsId(product == null ? "" : product.getGoodsId());
            item.setReferencePassed(containsAll(referenceText, evaluationCase.getRequiredReferenceKeywords()));
            item.setAnswerPassed(decisionResult.getIntent().getIntentType().equals(evaluationCase.getExpectedIntentType())
                    && containsAll(answerText, evaluationCase.getRequiredAnswerKeywords()));
            item.setRecommendationPassed(product != null
                    && evaluationCase.getExpectedGoodsId().equals(product.getGoodsId())
                    && decisionResult.getRecommendationResult().isPassedSelfCheck());
            item.setContextPassed(!evaluationCase.isContextRequired()
                    || containsAny(evaluationCase.getQuestion(), "刚才", "上一轮", "继续", "那", "这个"));
            item.setScore(score(item));
            item.setSuggestion(suggestion(item));
            return item;
        } catch (Exception e) {
            item.setSuggestion("用例执行失败，需要检查商品、活动或知识库数据：" + e.getMessage());
            item.setScore(0);
            return item;
        }
    }

    private GuideEvaluationItemResult baseItem(GuideEvaluationCase evaluationCase) {
        GuideEvaluationItemResult item = new GuideEvaluationItemResult();
        item.setCaseId(evaluationCase.getCaseId());
        item.setCaseName(evaluationCase.getCaseName());
        item.setQuestion(evaluationCase.getQuestion());
        item.setExpectedGoodsId(evaluationCase.getExpectedGoodsId());
        return item;
    }

    private boolean containsAll(String source, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String normalized = normalize(source);
        return keywords.stream().allMatch(keyword -> normalized.contains(normalize(keyword)));
    }

    private boolean containsAny(String source, String... keywords) {
        String normalized = normalize(source);
        for (String keyword : keywords) {
            if (normalized.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String source) {
        return StringUtils.hasText(source) ? source.toLowerCase(Locale.ROOT) : "";
    }

    private int score(GuideEvaluationItemResult item) {
        int score = 0;
        score += item.isReferencePassed() ? 25 : 0;
        score += item.isAnswerPassed() ? 25 : 0;
        score += item.isRecommendationPassed() ? 25 : 0;
        score += item.isContextPassed() ? 25 : 0;
        return score;
    }

    private String suggestion(GuideEvaluationItemResult item) {
        if (item.getScore() == 100) {
            return "通过";
        }
        if (!item.isReferencePassed()) {
            return "补充知识片段关键词或优化检索召回。";
        }
        if (!item.isAnswerPassed()) {
            return "调整回答模板，确保关键规则和价格信息进入答案。";
        }
        if (!item.isRecommendationPassed()) {
            return "检查推荐商品选择和商品资料完整性。";
        }
        return "补充多轮上下文提示，减少追问时的信息丢失。";
    }

    private BigDecimal rate(List<GuideEvaluationItemResult> items,
                            java.util.function.Predicate<GuideEvaluationItemResult> predicate) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long passed = items.stream().filter(predicate).count();
        return BigDecimal.valueOf(passed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }
}
