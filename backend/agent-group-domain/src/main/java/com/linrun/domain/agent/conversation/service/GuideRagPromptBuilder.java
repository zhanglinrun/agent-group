package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideRagPrompt;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.RecommendationReason;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;
import com.linrun.domain.agent.prompt.service.PromptTemplateService;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class GuideRagPromptBuilder {

    private final PromptTemplateService promptTemplateService;

    public GuideRagPromptBuilder(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public GuideRagPrompt build(String question, GuideDecisionResult decisionResult) {
        GuideRagPrompt prompt = new GuideRagPrompt();
        prompt.setSystemPrompt(promptTemplateService.requireEnabled(PromptTemplateType.GUIDE).getContent());
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

                额度包信息：
                %s

                推荐理由：
                %s

                推荐理由模板：
                %s

                知识片段：
                %s

                回答硬约束：
                1. 必须先给结论，再说明后端工具结果和知识依据。
                2. 涉及价格、活动、成团、名额、退款、支付、订单金额时，必须写出额度包名称、原价、拼团价、成团人数和依据片段。
                3. 不能把模型判断当成库存、价格或订单状态；这些高风险信息必须以后端工具结果为准。
                4. 对“不适合、不能、不会重复、需要重新校验、等待成团、补偿、幂等、防重放、Outbox”等边界结论，必须直接说清楚。

                自检模板：
                %s
                """.formatted(
                question,
                decisionResult.getIntent().getIntentType(),
                productContext(decisionResult.getProduct()),
                reasonContext(decisionResult),
                promptTemplateService.requireEnabled(PromptTemplateType.RECOMMEND_REASON).getContent(),
                referenceContext(decisionResult),
                promptTemplateService.requireEnabled(PromptTemplateType.SELF_CHECK).getContent());
    }

    private String productContext(GuideProduct product) {
        return """
                额度包编号：%s
                额度包名称：%s
                原价：%s
                拼团价：%s
                额度说明：%s
                退款规则：%s
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
                "我先结合额度包资料、拼团试算和知识片段给你结论。",
                "更建议优先看 " + product.getGoodsName() + "，当前拼团价是 " + product.getGroupPrice() + "，原价是 " + product.getOriginPrice() + "。",
                product.getRecommendReason(),
                "依据是：" + firstReference + " " + reasons);
    }
}
