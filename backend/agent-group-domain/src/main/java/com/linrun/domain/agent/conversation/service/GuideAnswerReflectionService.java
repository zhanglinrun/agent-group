package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.GuideAnswerReflection;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuideAnswerReflectionService {

    public GuideAnswerReflection reflect(String question,
                                         GuideDecisionResult decisionResult,
                                         List<String> answerSegments) {
        String answer = answerSegments == null ? "" : answerSegments.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (!StringUtils.hasText(answer)) {
            return GuideAnswerReflection.failed("answer is blank");
        }
        GuideProduct product = decisionResult == null ? null : decisionResult.getProduct();
        if (product == null) {
            return GuideAnswerReflection.failed("recommended product missing");
        }
        if (!contains(answer, product.getGoodsName()) && !contains(answer, product.getGoodsId())) {
            return GuideAnswerReflection.failed("answer does not mention recommended product");
        }
        if (isPriceQuestion(question) && product.getGroupPrice() != null
                && !answer.contains(product.getGroupPrice().toString())) {
            return GuideAnswerReflection.failed("answer misses backend group price");
        }
        if (isPolicyQuestion(question) && !hasPolicyEvidence(decisionResult.getReferences())) {
            return GuideAnswerReflection.failed("policy answer lacks knowledge reference");
        }
        if (containsAny(answer, "guaranteed", "must be in stock", "always refunded instantly")) {
            return GuideAnswerReflection.failed("answer contains risky absolute wording");
        }
        return GuideAnswerReflection.passed();
    }

    private boolean isPriceQuestion(String question) {
        String normalized = safe(question).toLowerCase();
        return containsAny(normalized, "price", "group price", "pay amount", "优惠", "价格", "拼团价", "金额");
    }

    private boolean isPolicyQuestion(String question) {
        String normalized = safe(question).toLowerCase();
        return containsAny(normalized, "refund", "return", "warranty", "退款", "退货", "售后", "质保");
    }

    private boolean hasPolicyEvidence(List<GuideReference> references) {
        if (references == null || references.isEmpty()) {
            return false;
        }
        return references.stream().anyMatch(reference -> containsAny(
                safe(reference.getDocumentType()) + safe(reference.getContent()),
                "refund", "return", "warranty", "退款", "退货", "售后", "质保"));
    }

    private boolean contains(String source, String expected) {
        return StringUtils.hasText(expected) && safe(source).contains(expected);
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
