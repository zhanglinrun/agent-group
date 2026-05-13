package com.linrun.domain.guide.service;

import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideIntent;
import com.linrun.domain.guide.model.GuideIntentType;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideRagPrompt;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.model.RecommendationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRagPromptBuilderTest {

    @Test
    void shouldBuildPromptWithProductAndReferences() {
        GuideRagPromptBuilder builder = new GuideRagPromptBuilder();

        GuideRagPrompt prompt = builder.build("拼团失败会退款吗", decisionResult());

        assertTrue(prompt.getSystemPrompt().contains("只基于商品资料"));
        assertTrue(prompt.getUserPrompt().contains("拼团失败会退款吗"));
        assertTrue(prompt.getUserPrompt().contains("轻薄学习平板标准版"));
        assertTrue(prompt.getUserPrompt().contains("未成团时系统自动退款"));
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
}
