package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideRagPrompt;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.RecommendationResult;
import com.linrun.domain.agent.prompt.adapter.PromptTemplateRepository;
import com.linrun.domain.agent.prompt.model.PromptTemplate;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;
import com.linrun.domain.agent.prompt.service.PromptTemplateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRagPromptBuilderTest {

    @Test
    void shouldBuildPromptWithProductAndReferences() {
        GuideRagPromptBuilder builder = new GuideRagPromptBuilder(promptTemplateService());

        GuideRagPrompt prompt = builder.build("拼团失败会退款吗", decisionResult());

        assertTrue(prompt.getSystemPrompt().contains("只基于商品资料"));
        assertTrue(prompt.getUserPrompt().contains("拼团失败会退款吗"));
        assertTrue(prompt.getUserPrompt().contains("轻薄学习平板标准版"));
        assertTrue(prompt.getUserPrompt().contains("未成团时系统自动退款"));
        assertTrue(prompt.getUserPrompt().contains("推荐理由需要覆盖用户身份"));
        assertTrue(prompt.getUserPrompt().contains("回答前检查"));
        assertTrue(prompt.getFallbackAnswer().contains("当前拼团价是 2099.00"));
    }

    static GuideDecisionResult decisionResult() {
        GuideIntent intent = new GuideIntent();
        intent.setIntentType(GuideIntentType.AFTER_SALE);

        GuideProduct product = new GuideProduct();
        product.setGoodsId("G10001");
        product.setGoodsName("轻薄学习平板标准版");
        product.setOriginPrice(new BigDecimal("2399.00"));
        product.setGroupPrice(new BigDecimal("2099.00"));
        product.setSpecSummary("10.9 英寸屏幕");
        product.setAfterSalePolicy("7 天无理由退货");
        product.setRecommendReason("预算有限时性价比更高");
        product.setActivityId("A10001");
        product.setTeamSize(3);
        product.setRemainingSeconds(1800);

        GuideReference reference = new GuideReference();
        reference.setFragmentId("KF10001");
        reference.setDocumentType("售后政策");
        reference.setContent("未成团时系统自动退款。");

        RecommendationResult recommendationResult = new RecommendationResult();
        recommendationResult.setPrimaryProduct(product);
        recommendationResult.addReason("AFTER_SALE_MATCH", "你关注售后，需要看退货和退款规则。", 90);

        GuideDecisionResult result = new GuideDecisionResult();
        result.setIntent(intent);
        result.setProduct(product);
        result.setReferences(List.of(reference));
        result.setRecommendationResult(recommendationResult);
        return result;
    }

    static PromptTemplateService promptTemplateService() {
        return new PromptTemplateService(new FakePromptTemplateRepository());
    }

    private static class FakePromptTemplateRepository implements PromptTemplateRepository {

        private final List<PromptTemplate> templates = List.of(
                PromptTemplate.enabled("PT-GUIDE-001", PromptTemplateType.GUIDE, "guide-v1.0", "只基于商品资料回答。"),
                PromptTemplate.enabled("PT-REASON-001", PromptTemplateType.RECOMMEND_REASON, "reason-v1.0", "推荐理由需要覆盖用户身份。"),
                PromptTemplate.enabled("PT-CHECK-001", PromptTemplateType.SELF_CHECK, "self-check-v1.0", "回答前检查资料是否齐全。")
        );

        @Override
        public Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType) {
            return templates.stream()
                    .filter(template -> template.getTemplateType().equals(templateType))
                    .findFirst();
        }

        @Override
        public List<PromptTemplate> queryEnabledTemplates() {
            return templates;
        }
    }
}
