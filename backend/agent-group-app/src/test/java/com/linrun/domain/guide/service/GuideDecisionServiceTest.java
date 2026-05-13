package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideIntentType;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideDecisionServiceTest {

    @Test
    void shouldRecognizeBudgetStudentAndRecommendProduct() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository());

        GuideDecisionResult result = service.decide("我是学生，预算有限，想买适合写论文和看网课的平板，哪款更合适？");

        assertEquals(GuideIntentType.PRODUCT_COMPARE, result.getIntent().getIntentType());
        assertEquals("学生", result.getIntent().getUserIdentity());
        assertTrue(result.getIntent().isBudgetSensitive());
        assertTrue(result.getIntent().isCompareConcerned());
        assertEquals(List.of("文档写作", "网课学习"), result.getIntent().getUsageScenarios());
        assertEquals("G10001", result.getProduct().getGoodsId());
        assertEquals(2, result.getReferences().size());
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("预算")));
    }

    @Test
    void shouldRecognizeAfterSaleIntent() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository());

        GuideDecisionResult result = service.decide("拼团失败会退款吗，售后怎么处理？");

        assertEquals(GuideIntentType.AFTER_SALE, result.getIntent().getIntentType());
        assertTrue(result.getIntent().isAfterSaleConcerned());
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("售后")));
    }

    @Test
    void shouldThrowWhenQuestionIsBlank() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository());

        AppException exception = assertThrows(AppException.class, () -> service.decide(" "));

        assertEquals("0001", exception.getCode());
        assertEquals("问题不能为空", exception.getMessage());
    }

    @Test
    void shouldThrowWhenProductIsMissing() {
        GuideDecisionService service = new GuideDecisionService(new EmptyGuideDataRepository());

        AppException exception = assertThrows(AppException.class, () -> service.decide("推荐一款平板"));

        assertEquals("DATA_0002", exception.getCode());
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of(reference("KF10001", 1), reference("KF10002", 2));
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("轻薄学习平板标准版");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            product.setNotSuitableFor("长期剪视频或运行大型应用的用户");
            product.setActivityId("A10001");
            product.setTeamSize(3);
            product.setRemainingSeconds(1800);
            return Optional.of(product);
        }

        private GuideReference reference(String fragmentId, int rank) {
            GuideReference reference = new GuideReference();
            reference.setFragmentId(fragmentId);
            reference.setDocumentId("DOC10001");
            reference.setGoodsId("G10001");
            reference.setDocumentType("商品详情");
            reference.setKnowledgeVersion("v1");
            reference.setContent("轻薄学习平板标准版适合写论文、看网课和日常笔记。");
            reference.setRank(rank);
            return reference;
        }
    }

    private static class EmptyGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return Optional.empty();
        }
    }
}
