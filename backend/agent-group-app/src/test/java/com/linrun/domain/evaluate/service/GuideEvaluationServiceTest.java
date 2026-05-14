package com.linrun.domain.evaluate.service;

import com.linrun.domain.evaluate.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.evaluate.model.GuideEvaluationCase;
import com.linrun.domain.evaluate.model.GuideEvaluationReport;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideIntentType;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.service.GuideDecisionService;
import com.linrun.domain.guide.service.GuideRagAnswerService;
import com.linrun.domain.guide.service.GuideRagPromptBuilder;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.service.GroupBuyActivityService;
import com.linrun.domain.prompt.service.PromptTemplateService;
import com.linrun.infrastructure.prompt.LocalPromptTemplateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideEvaluationServiceTest {

    @Test
    void shouldRunBatchAndCalculateQualityMetrics() {
        GuideEvaluationService service = new GuideEvaluationService(
                new FakeGuideEvaluationCaseRepository(),
                new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService()),
                new GuideRagAnswerService(
                        new GuideRagPromptBuilder(new PromptTemplateService(new LocalPromptTemplateRepository())),
                        prompt -> prompt.getFallbackAnswer()));

        GuideEvaluationReport report = service.runBatch();

        assertEquals(2, report.getTotalCount());
        assertEquals(new BigDecimal("100.00"), report.getRetrievalHitRate());
        assertEquals(new BigDecimal("100.00"), report.getAnswerAccuracyRate());
        assertEquals(new BigDecimal("100.00"), report.getRecommendationReasonableRate());
        assertEquals(new BigDecimal("100.00"), report.getContextConsistencyRate());
        assertEquals(100, report.getItems().get(0).getScore());
        assertEquals(1, report.getFeedbacks().size());
        assertEquals("QUALITY", report.getFeedbacks().get(0).getTargetType());
    }

    private static GroupBuyActivityService groupBuyService() {
        return new GroupBuyActivityService(new ActiveGroupBuyActivityRepository());
    }

    private static class FakeGuideEvaluationCaseRepository implements GuideEvaluationCaseRepository {

        @Override
        public List<GuideEvaluationCase> queryEnabledCases() {
            return List.of(
                    evaluationCase("EV10001", "学生预算导购", "我是学生，预算有限，想买适合看网课的平板",
                            GuideIntentType.PRODUCT_RECOMMEND, false, List.of("学习"), List.of("2099")),
                    evaluationCase("EV10002", "售后追问", "那上一轮推荐的商品售后怎么样",
                            GuideIntentType.AFTER_SALE, true, List.of("售后"), List.of("售后"))
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
            reference.setDocumentType("商品详情");
            reference.setKnowledgeVersion("v1");
            reference.setContent("轻薄学习平板标准版适合学习、网课、论文和笔记，售后支持退货和质保。");
            reference.setRank(1);
            return List.of(reference);
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("轻薄学习平板标准版");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            product.setNotSuitableFor("长期剪视频或运行大型应用的用户");
            return Optional.of(product);
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryRecommendProduct(goodsId);
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
