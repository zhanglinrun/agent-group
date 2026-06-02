package com.linrun.domain.agent.quality.service;

import com.linrun.domain.agent.quality.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.agent.quality.adapter.GuideEvaluationReportRepository;
import com.linrun.domain.agent.quality.model.GuideEvaluationCase;
import com.linrun.domain.agent.quality.model.GuideEvaluationFeedback;
import com.linrun.domain.agent.quality.model.GuideEvaluationItemResult;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.agent.conversation.service.AgentPlannerService;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.agent.conversation.service.GuideDecisionService;
import com.linrun.domain.agent.conversation.service.GuideRagAnswerService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Service
public class GuideEvaluationService {

    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final BigDecimal TOOL_CALL_GATE = new BigDecimal("95.00");
    private static final BigDecimal TOOL_RESULT_REFERENCE_GATE = new BigDecimal("90.00");
    private static final BigDecimal ANSWER_ACCURACY_GATE = new BigDecimal("85.00");

    private final GuideEvaluationCaseRepository guideEvaluationCaseRepository;
    private final GuideEvaluationReportRepository guideEvaluationReportRepository;
    private final AgentPlannerService agentPlannerService;
    private final GuideDecisionService guideDecisionService;
    private final GuideRagAnswerService guideRagAnswerService;

    public GuideEvaluationService(GuideEvaluationCaseRepository guideEvaluationCaseRepository,
                                  GuideEvaluationReportRepository guideEvaluationReportRepository,
                                  AgentPlannerService agentPlannerService,
                                  GuideDecisionService guideDecisionService,
                                  GuideRagAnswerService guideRagAnswerService) {
        this.guideEvaluationCaseRepository = guideEvaluationCaseRepository;
        this.guideEvaluationReportRepository = guideEvaluationReportRepository;
        this.agentPlannerService = agentPlannerService;
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
        report.setPromptVersion("guide-v1.2/tool-plan-v1.1/self-check-v1.1");
        report.setKnowledgeVersion("v1");
        report.setTotalCount(items.size());
        report.setRetrievalHitRate(rate(items, GuideEvaluationItemResult::isReferencePassed));
        report.setAnswerAccuracyRate(rate(items, GuideEvaluationItemResult::isAnswerPassed));
        report.setRecommendationReasonableRate(rate(items, GuideEvaluationItemResult::isRecommendationPassed));
        report.setContextConsistencyRate(rate(items, GuideEvaluationItemResult::isContextPassed));
        report.setToolCallAccuracyRate(rate(items, GuideEvaluationItemResult::isToolCallPassed));
        report.setToolArgumentAccuracyRate(rate(items, GuideEvaluationItemResult::isToolArgumentPassed));
        report.setToolResultReferenceRate(rate(items, GuideEvaluationItemResult::isToolResultReferencePassed));
        report.setAverageLatencyMillis(averageLatencyMillis(items));
        report.setP99LatencyMillis(percentileLatencyMillis(items, 0.99D));
        report.setTotalPromptTokens(sum(items, GuideEvaluationItemResult::getPromptTokens));
        report.setTotalCompletionTokens(sum(items, GuideEvaluationItemResult::getCompletionTokens));
        report.setTotalTokens(sum(items, GuideEvaluationItemResult::getTotalTokens));
        report.setEstimatedCostYuan(totalEstimatedCostYuan(items));
        report.setItems(items);
        fillBaselineDelta(report);
        report.setFeedbacks(buildFeedbacks(report, items));
        guideEvaluationReportRepository.save(report);
        return report;
    }

    public GuideEvaluationReport queryLatestReport() {
        return guideEvaluationReportRepository.queryLatest()
                .orElseGet(GuideEvaluationReport::new);
    }

    private void fillBaselineDelta(GuideEvaluationReport report) {
        guideEvaluationReportRepository.queryLatest().ifPresent(previous -> {
            report.setBaselineBatchNo(previous.getBatchNo());
            report.setRetrievalHitRateDelta(delta(report.getRetrievalHitRate(), previous.getRetrievalHitRate()));
            report.setAnswerAccuracyRateDelta(delta(report.getAnswerAccuracyRate(), previous.getAnswerAccuracyRate()));
            report.setRecommendationReasonableRateDelta(delta(report.getRecommendationReasonableRate(),
                    previous.getRecommendationReasonableRate()));
            report.setContextConsistencyRateDelta(delta(report.getContextConsistencyRate(),
                    previous.getContextConsistencyRate()));
        });
    }

    private BigDecimal delta(BigDecimal current, BigDecimal previous) {
        return zero(current).subtract(zero(previous)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private GuideEvaluationItemResult evaluateOne(GuideEvaluationCase evaluationCase) {
        long startNanos = System.nanoTime();
        GuideEvaluationItemResult item = baseItem(evaluationCase);
        try {
            AgentPlan agentPlan = agentPlannerService.plan(evaluationCase.getQuestion());
            GuideDecisionResult decisionResult = guideDecisionService.decide(evaluationCase.getQuestion());
            agentPlannerService.fillRuntimeArguments(agentPlan, decisionResult);
            item.setActualToolNames(String.join(",", agentPlan.toolNames()));
            item.setToolCallPassed(toolCallPassed(agentPlan, evaluationCase));
            item.setToolArgumentPassed(toolArgumentPassed(agentPlan));

            GuideRagAnswerResult answerResult = guideRagAnswerService.answerWithMetrics(evaluationCase.getQuestion(), decisionResult);
            List<String> answerSegments = answerResult.getSegments();
            String referenceText = decisionResult.getReferences().stream()
                    .map(GuideReference::getContent)
                    .collect(Collectors.joining("\n"));
            String answerText = String.join("\n", answerSegments);
            GuideProduct product = decisionResult.getProduct();
            fillTokenUsage(item, answerResult);

            item.setActualGoodsId(product == null ? "" : product.getGoodsId());
            item.setReferencePassed(containsAll(referenceText, evaluationCase.getRequiredReferenceKeywords()));
            item.setAnswerPassed(decisionResult.getIntent().getIntentType().equals(evaluationCase.getExpectedIntentType())
                    && containsAll(answerText, evaluationCase.getRequiredAnswerKeywords())
                    && containsNone(answerText, evaluationCase.getForbiddenAnswerKeywords()));
            item.setRecommendationPassed(product != null
                    && evaluationCase.getExpectedGoodsId().equals(product.getGoodsId())
                    && decisionResult.getRecommendationResult().isPassedSelfCheck());
            item.setContextPassed(!evaluationCase.isContextRequired()
                    || containsAny(evaluationCase.getQuestion(), "刚才", "上一轮", "继续", "那", "这个"));
            item.setToolResultReferencePassed(toolResultReferencePassed(agentPlan, item, answerText));
            item.setScore(score(item));
            item.setSuggestion(suggestion(item));
            return item;
        } catch (Exception e) {
            item.setSuggestion("用例执行失败，需要检查额度包、活动或知识库数据：" + e.getMessage());
            item.setScore(0);
            return item;
        } finally {
            item.setLatencyMillis(elapsedMillis(startNanos));
        }
    }

    private void fillTokenUsage(GuideEvaluationItemResult item, GuideRagAnswerResult answerResult) {
        GuideTokenUsage tokenUsage = answerResult.getTokenUsage();
        item.setPromptTokens(tokenUsage.getPromptTokens());
        item.setCompletionTokens(tokenUsage.getCompletionTokens());
        item.setTotalTokens(tokenUsage.getTotalTokens());
        item.setEstimatedCostYuan(tokenUsage.getEstimatedCostYuan());
        item.setLlmLatencyMillis(answerResult.getLlmLatencyMillis());
        item.setFallbackUsed(answerResult.isFallbackUsed());
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

    private boolean containsNone(String source, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String normalized = normalize(source);
        return keywords.stream().noneMatch(keyword -> normalized.contains(normalize(keyword)));
    }

    private String normalize(String source) {
        return StringUtils.hasText(source) ? source.toLowerCase(Locale.ROOT) : "";
    }

    private int score(GuideEvaluationItemResult item) {
        List<Boolean> checks = List.of(
                item.isReferencePassed(),
                item.isAnswerPassed(),
                item.isRecommendationPassed(),
                item.isContextPassed(),
                item.isToolCallPassed(),
                item.isToolArgumentPassed(),
                item.isToolResultReferencePassed());
        long passed = checks.stream().filter(Boolean::booleanValue).count();
        return (int) Math.round(passed * 100D / checks.size());
    }

    private String suggestion(GuideEvaluationItemResult item) {
        if (item.getScore() == 100) {
            return "通过";
        }
        if (!item.isToolCallPassed()) {
            return "检查工具规划规则，确保问题能选择正确工具。";
        }
        if (!item.isToolArgumentPassed()) {
            return "检查工具参数抽取和占位参数回填。";
        }
        if (!item.isToolResultReferencePassed()) {
            return "检查回答是否引用工具结果，避免工具查了但答案没用。";
        }
        if (!item.isReferencePassed()) {
            return "补充知识片段关键词或优化检索召回。";
        }
        if (!item.isAnswerPassed()) {
            return "调整回答模板，确保关键规则和价格信息进入答案。";
        }
        if (!item.isRecommendationPassed()) {
            return "检查推荐额度包选择和资料完整性。";
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

    private long averageLatencyMillis(List<GuideEvaluationItemResult> items) {
        if (items.isEmpty()) {
            return 0L;
        }
        return Math.round(items.stream()
                .mapToLong(GuideEvaluationItemResult::getLatencyMillis)
                .average()
                .orElse(0D));
    }

    private long percentileLatencyMillis(List<GuideEvaluationItemResult> items, double percentile) {
        if (items.isEmpty()) {
            return 0L;
        }
        List<Long> latencies = items.stream()
                .map(GuideEvaluationItemResult::getLatencyMillis)
                .sorted()
                .toList();
        int index = (int) Math.ceil(latencies.size() * percentile) - 1;
        return latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
    }

    private long sum(List<GuideEvaluationItemResult> items, ToLongFunction<GuideEvaluationItemResult> mapper) {
        return items.stream().mapToLong(mapper).sum();
    }

    private BigDecimal totalEstimatedCostYuan(List<GuideEvaluationItemResult> items) {
        return items.stream()
                .map(GuideEvaluationItemResult::getEstimatedCostYuan)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private boolean toolCallPassed(AgentPlan agentPlan, GuideEvaluationCase evaluationCase) {
        List<String> actualToolNames = agentPlan.toolNames();
        List<String> expectedToolOrder = evaluationCase.getExpectedToolOrder();
        if (expectedToolOrder != null && !expectedToolOrder.isEmpty()) {
            return actualToolNames.equals(expectedToolOrder);
        }
        List<String> expectedToolNames = evaluationCase.getExpectedToolNames();
        if (expectedToolNames == null || expectedToolNames.isEmpty()) {
            return !actualToolNames.isEmpty();
        }
        return actualToolNames.containsAll(expectedToolNames);
    }

    private boolean toolArgumentPassed(AgentPlan agentPlan) {
        if (agentPlannerService.hasRuntimePlaceholder(agentPlan)) {
            return false;
        }
        return agentPlan.getTools().stream().allMatch(tool -> {
            if (AgentToolRegistry.KNOWLEDGE_SEARCH.equals(tool.getName())
                    || AgentToolRegistry.GUIDE_RECOMMEND.equals(tool.getName())
                    || AgentToolRegistry.ORDER_STATUS.equals(tool.getName())) {
                return StringUtils.hasText(tool.getArguments().get("question"));
            }
            if (AgentToolRegistry.GROUP_TRIAL.equals(tool.getName())) {
                return StringUtils.hasText(tool.getArguments().get("goodsId"));
            }
            return false;
        });
    }

    private boolean toolResultReferencePassed(AgentPlan agentPlan, GuideEvaluationItemResult item, String answerText) {
        if (agentPlan.hasTool(AgentToolRegistry.ORDER_STATUS)) {
            return item.isToolArgumentPassed();
        }
        if (agentPlan.hasTool(AgentToolRegistry.KNOWLEDGE_SEARCH) && !item.isReferencePassed()) {
            return false;
        }
        if (agentPlan.hasTool(AgentToolRegistry.GROUP_TRIAL)
                && !containsAny(answerText,
                "拼团价", "成团", "活动", "原价", "额度", "额度包", "19.90", "29.90", "59.90", "109.90",
                "后端", "工具", "金额", "订单金额", "支付单", "锁单", "库存",
                "退款", "幂等", "防重放", "补偿", "Outbox")) {
            return false;
        }
        return true;
    }

    private List<GuideEvaluationFeedback> buildFeedbacks(GuideEvaluationReport report, List<GuideEvaluationItemResult> items) {
        List<GuideEvaluationFeedback> feedbacks = new java.util.ArrayList<>();
        long referenceFailed = items.stream().filter(item -> !item.isReferencePassed()).count();
        long answerFailed = items.stream().filter(item -> !item.isAnswerPassed()).count();
        long recommendationFailed = items.stream().filter(item -> !item.isRecommendationPassed()).count();
        long contextFailed = items.stream().filter(item -> !item.isContextPassed()).count();
        long toolFailed = items.stream().filter(item -> !item.isToolCallPassed()
                || !item.isToolArgumentPassed()
                || !item.isToolResultReferencePassed()).count();

        if (toolFailed > 0) {
            feedbacks.add(new GuideEvaluationFeedback("TOOL", "HIGH",
                    "有" + toolFailed + "个用例工具规划、参数或结果引用未通过，优先检查工具白名单和回答约束。"));
        }
        if (referenceFailed > 0) {
            feedbacks.add(new GuideEvaluationFeedback("KNOWLEDGE", "HIGH",
                    "有" + referenceFailed + "个用例检索依据未命中，优先补充额度包说明、活动规则或退款规则片段。"));
        }
        if (answerFailed > 0) {
            feedbacks.add(new GuideEvaluationFeedback("PROMPT", "HIGH",
                    "有" + answerFailed + "个用例回答缺少关键结论，建议调整额度包回答模板，强制输出价格、规则和适用边界。"));
        }
        if (recommendationFailed > 0) {
            feedbacks.add(new GuideEvaluationFeedback("RECOMMENDATION", "MEDIUM",
                    "有" + recommendationFailed + "个用例推荐额度包不符合预期，建议复核额度包标签、候选排序和自检规则。"));
        }
        if (contextFailed > 0) {
            feedbacks.add(new GuideEvaluationFeedback("CONTEXT", "MEDIUM",
                    "有" + contextFailed + "个多轮用例上下文不一致，建议补充最近对话摘要和追问指代消解。"));
        }
        if (gateFailed(report)) {
            feedbacks.add(new GuideEvaluationFeedback("REGRESSION_GATE", "HIGH",
                    "本批次未达到回归门禁：工具调用正确率需不低于95%，工具结果引用率需不低于90%，回答准确率需不低于85%。"));
        }
        if (feedbacks.isEmpty()) {
            feedbacks.add(new GuideEvaluationFeedback("QUALITY", "LOW",
                    "本批次评测全部通过，保留当前提示词和知识版本，继续扩展更复杂的真实额度用例。"));
        }
        return feedbacks;
    }

    private boolean gateFailed(GuideEvaluationReport report) {
        return below(report.getToolCallAccuracyRate(), TOOL_CALL_GATE)
                || below(report.getToolResultReferenceRate(), TOOL_RESULT_REFERENCE_GATE)
                || below(report.getAnswerAccuracyRate(), ANSWER_ACCURACY_GATE);
    }

    private boolean below(BigDecimal value, BigDecimal gate) {
        return zero(value).compareTo(gate) < 0;
    }
}
