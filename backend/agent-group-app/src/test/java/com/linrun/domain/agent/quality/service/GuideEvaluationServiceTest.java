package com.linrun.domain.agent.quality.service;

import com.linrun.domain.agent.quality.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.agent.quality.adapter.GuideEvaluationReportRepository;
import com.linrun.domain.agent.quality.model.GuideEvaluationCase;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.service.GuideDecisionService;
import com.linrun.domain.agent.conversation.service.GuideRagAnswerService;
import com.linrun.domain.agent.conversation.service.GuideRagPromptBuilder;
import com.linrun.domain.agent.conversation.service.AgentPlannerService;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.activity.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.prompt.service.PromptTemplateService;
import com.linrun.infrastructure.adapter.repository.LocalPromptTemplateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideEvaluationServiceTest {

    @Test
    void shouldRunBatchAndCalculateQualityMetrics() {
        FakeGuideEvaluationReportRepository reportRepository = new FakeGuideEvaluationReportRepository();
        GuideDecisionService guideDecisionService = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());
        AgentToolRegistry agentToolRegistry = new AgentToolRegistry();
        GuideEvaluationService service = new GuideEvaluationService(
                new FakeGuideEvaluationCaseRepository(),
                reportRepository,
                new AgentPlannerService(guideDecisionService, agentToolRegistry),
                guideDecisionService,
                new GuideRagAnswerService(
                        new GuideRagPromptBuilder(new PromptTemplateService(new LocalPromptTemplateRepository())),
                        prompt -> prompt.getFallbackAnswer()));

        GuideEvaluationReport report = service.runBatch();

        assertEquals(2, report.getTotalCount());
        assertEquals(new BigDecimal("100.00"), report.getRetrievalHitRate());
        assertEquals(new BigDecimal("100.00"), report.getAnswerAccuracyRate());
        assertEquals(new BigDecimal("100.00"), report.getRecommendationReasonableRate());
        assertEquals(new BigDecimal("100.00"), report.getContextConsistencyRate());
        assertEquals(new BigDecimal("100.00"), report.getToolCallAccuracyRate());
        assertEquals(new BigDecimal("100.00"), report.getToolArgumentAccuracyRate());
        assertEquals(new BigDecimal("100.00"), report.getToolResultReferenceRate());
        assertTrue(report.getAverageLatencyMillis() >= 0);
        assertTrue(report.getP99LatencyMillis() >= 0);
        assertEquals(0L, report.getTotalTokens());
        assertEquals(100, report.getItems().get(0).getScore());
        assertTrue(report.getItems().get(0).getLatencyMillis() >= 0);
        assertEquals(1, report.getFeedbacks().size());
        assertEquals("QUALITY", report.getFeedbacks().get(0).getTargetType());
        assertEquals(report.getBatchNo(), reportRepository.queryLatest().orElseThrow().getBatchNo());
    }

    @Test
    void shouldCompareCurrentReportWithLatestPersistedBaseline() {
        FakeGuideEvaluationReportRepository reportRepository = new FakeGuideEvaluationReportRepository();
        GuideEvaluationReport baseline = new GuideEvaluationReport();
        baseline.setBatchNo("EVAL20260518090000000");
        baseline.setRetrievalHitRate(new BigDecimal("75.00"));
        baseline.setAnswerAccuracyRate(new BigDecimal("80.00"));
        baseline.setRecommendationReasonableRate(new BigDecimal("90.00"));
        baseline.setContextConsistencyRate(new BigDecimal("100.00"));
        reportRepository.save(baseline);

        GuideDecisionService guideDecisionService = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());
        AgentToolRegistry agentToolRegistry = new AgentToolRegistry();
        GuideEvaluationService service = new GuideEvaluationService(
                new FakeGuideEvaluationCaseRepository(),
                reportRepository,
                new AgentPlannerService(guideDecisionService, agentToolRegistry),
                guideDecisionService,
                new GuideRagAnswerService(
                        new GuideRagPromptBuilder(new PromptTemplateService(new LocalPromptTemplateRepository())),
                        prompt -> prompt.getFallbackAnswer()));

        GuideEvaluationReport report = service.runBatch();

        assertEquals("EVAL20260518090000000", report.getBaselineBatchNo());
        assertEquals(new BigDecimal("25.00"), report.getRetrievalHitRateDelta());
        assertEquals(new BigDecimal("20.00"), report.getAnswerAccuracyRateDelta());
        assertEquals(new BigDecimal("10.00"), report.getRecommendationReasonableRateDelta());
        assertEquals(new BigDecimal("0.00"), report.getContextConsistencyRateDelta());
        assertEquals(report.getBatchNo(), service.queryLatestReport().getBatchNo());
    }

    @Test
    void shouldAddRegressionGateFeedbackWhenCoreMetricIsLow() {
        FakeGuideEvaluationReportRepository reportRepository = new FakeGuideEvaluationReportRepository();
        GuideDecisionService guideDecisionService = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());
        AgentToolRegistry agentToolRegistry = new AgentToolRegistry();
        GuideEvaluationService service = new GuideEvaluationService(
                () -> List.of(failingEvaluationCase()),
                reportRepository,
                new AgentPlannerService(guideDecisionService, agentToolRegistry),
                guideDecisionService,
                new GuideRagAnswerService(
                        new GuideRagPromptBuilder(new PromptTemplateService(new LocalPromptTemplateRepository())),
                        prompt -> prompt.getFallbackAnswer()));

        GuideEvaluationReport report = service.runBatch();

        assertTrue(report.getFeedbacks().stream()
                .anyMatch(feedback -> "REGRESSION_GATE".equals(feedback.getTargetType())));
    }

    private static GroupBuyActivityService groupBuyService() {
        return new GroupBuyActivityService(new ActiveGroupBuyActivityRepository());
    }

    private static GuideEvaluationCase failingEvaluationCase() {
        GuideEvaluationCase evaluationCase = new GuideEvaluationCase();
        evaluationCase.setCaseId("EV90001");
        evaluationCase.setCaseName("错误答案门禁");
        evaluationCase.setQuestion("我是研究生，预算有限，想买适合普通学术问答的额度包");
        evaluationCase.setExpectedIntentType(GuideIntentType.PRODUCT_RECOMMEND);
        evaluationCase.setExpectedGoodsId("G10001");
        evaluationCase.setRequiredReferenceKeywords(List.of("不存在的依据"));
        evaluationCase.setRequiredAnswerKeywords(List.of("不存在的答案"));
        return evaluationCase;
    }

    private static class FakeGuideEvaluationCaseRepository implements GuideEvaluationCaseRepository {

        @Override
        public List<GuideEvaluationCase> queryEnabledCases() {
            return List.of(
                    evaluationCase("EV10001", "学生预算额度包", "我是研究生，预算有限，想买适合普通学术问答的额度包",
                            GuideIntentType.PRODUCT_RECOMMEND, false, List.of("普通学术问答"), List.of("2099")),
                    evaluationCase("EV10002", "退款追问", "那上一轮推荐的额度包退款规则怎么样",
                            GuideIntentType.AFTER_SALE, true, List.of("退款"), List.of("退款"))
            );
        }

        private GuideEvaluationCase evaluationCase(String caseId,
                                                   String caseName,
                                                   String question,
                                                   GuideIntentType intentType,
                                                   boolean contextRequired,
                                                   List<String> referenceKeywords,
                                                   List<String> answerKeywords) {
            GuideEvaluationCase evaluationCase = new GuideEvaluationCase();
            evaluationCase.setCaseId(caseId);
            evaluationCase.setCaseName(caseName);
            evaluationCase.setQuestion(question);
            evaluationCase.setExpectedIntentType(intentType);
            evaluationCase.setExpectedGoodsId("G10001");
            evaluationCase.setContextRequired(contextRequired);
            evaluationCase.setRequiredReferenceKeywords(referenceKeywords);
            evaluationCase.setRequiredAnswerKeywords(answerKeywords);
            return evaluationCase;
        }
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            GuideReference reference = new GuideReference();
            reference.setFragmentId("KF10001");
            reference.setDocumentId("DOC10001");
            reference.setGoodsId("G10001");
            reference.setDocumentType("额度包资料");
            reference.setKnowledgeVersion("v1");
            reference.setContent("基础学术额度包适合普通学术问答、论文摘要和资料整理，拼团失败支持退款。");
            reference.setRank(1);
            return List.of(reference);
        }

        @Override
        public List<GuideProduct> queryCandidateProducts(String question, int limit) {
            return queryRecommendProduct(question).stream().toList();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("基础学术额度包");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("40 次普通学术问答额度，适合摘要和资料整理");
            product.setAfterSalePolicy("直接购买支付成功后发放额度，拼团需成团后发放额度；拼团失败自动退款");
            product.setRecommendReason("预算有限、普通学术问答和资料整理场景下性价比更高");
            product.setNotSuitableFor("长文档批量精读、复杂复现或团队共享场景");
            return Optional.of(product);
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryRecommendProduct(goodsId);
        }
    }

    private static class FakeGuideEvaluationReportRepository implements GuideEvaluationReportRepository {

        private final AtomicReference<GuideEvaluationReport> latest = new AtomicReference<>();

        @Override
        public void save(GuideEvaluationReport report) {
            latest.set(report);
        }

        @Override
        public Optional<GuideEvaluationReport> queryLatest() {
            return Optional.ofNullable(latest.get());
        }
    }

    private static class ActiveGroupBuyActivityRepository implements GroupBuyActivityRepository {

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            GroupBuyActivity activity = new GroupBuyActivity();
            activity.setId(1L);
            activity.setActivityId("A10001");
            activity.setGoodsId(goodsId);
            activity.setGroupPrice(new BigDecimal("2099.00"));
            activity.setTeamSize(3);
            activity.setStartTime(LocalDateTime.now().minusMinutes(10));
            activity.setEndTime(LocalDateTime.now().plusMinutes(30));
            activity.setEnabled(true);
            return Optional.of(activity);
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.empty();
        }
    }
}
