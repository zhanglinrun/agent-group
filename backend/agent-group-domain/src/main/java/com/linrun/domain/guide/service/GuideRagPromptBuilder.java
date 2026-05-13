package com.linrun.domain.guide.service;

import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideRagPrompt;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.model.RecommendationReason;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class GuideRagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是电商 AI 导购助手。回答必须只基于商品资料、拼团试算和知识片段；信息不足时要明确说明待补充，不要编造。
            输出要适合流式展示，先给结论，再给依据，最后给购买或售后提醒。
            """;

    public GuideRagPrompt build(String question, GuideDecisionResult decisionResult) {
        GuideRagPrompt prompt = new GuideRagPrompt();
        prompt.setSystemPrompt(SYSTEM_PROMPT);
        prompt.setUserPrompt(buildUserPrompt(question, decisionResult));
        prompt.setFallbackAnswer(buildFallbackAnswer(decisionResult));
        return prompt;
    }

    private String buildUserPrompt(String question, GuideDecisionResult decisionResult) {
        return """
                用户问题：
                %s

                意图识别：
                %s

                商品信息：
                %s

                推荐理由：
                %s

                知识片段：
                %s
                """.formatted(
                question,
                decisionResult.getIntent().getIntentType(),
                productContext(decisionResult.getProduct()),
                reasonContext(decisionResult),
                referenceContext(decisionResult));
    }

    private String productContext(GuideProduct product) {
        return """
                商品编号：%s
                商品名称：%s
                原价：%s
                拼团价：%s
                规格：%s
                售后：%s
                活动编号：%s
                成团人数：%s
                剩余秒数：%s
                """.formatted(
                product.getGoodsId(),
                product.getGoodsName(),
                product.getOriginPrice(),
                product.getGroupPrice(),
                product.getSpecSummary(),
                product.getAfterSalePolicy(),
                product.getActivityId(),
                product.getTeamSize(),
                product.getRemainingSeconds());
    }

    private String reasonContext(GuideDecisionResult decisionResult) {
        return decisionResult.getRecommendationResult().getReasons().stream()
                .map(reason -> reason.getReasonType() + "：" + reason.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String referenceContext(GuideDecisionResult decisionResult) {
        return decisionResult.getReferences().stream()
                .map(this::referenceLine)
                .collect(Collectors.joining("\n"));
    }

    private String referenceLine(GuideReference reference) {
        return "[" + reference.getDocumentType() + "/" + reference.getFragmentId() + "] " + reference.getContent();
    }

    private String buildFallbackAnswer(GuideDecisionResult decisionResult) {
        GuideProduct product = decisionResult.getProduct();
        String firstReference = decisionResult.getReferences().isEmpty()
                ? "当前知识库没有命中的补充片段。"
                : decisionResult.getReferences().get(0).getContent();
        String reasons = decisionResult.getRecommendationResult().getReasons().stream()
                .map(RecommendationReason::getContent)
                .limit(2)
                .collect(Collectors.joining(" "));
        return String.join("\n",
                "我先结合商品资料、拼团试算和知识片段给你结论。",
                "更建议优先看 " + product.getGoodsName() + "，当前拼团价是 " + product.getGroupPrice() + "，原价是 " + product.getOriginPrice() + "。",
                product.getRecommendReason(),
                "依据是：" + firstReference + " " + reasons);
    }
}
