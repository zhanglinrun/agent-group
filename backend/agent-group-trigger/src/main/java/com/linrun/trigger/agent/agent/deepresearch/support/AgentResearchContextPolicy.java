package com.linrun.trigger.agent.agent.deepresearch.support;

import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.domain.agent.memory.model.UserAgentMemorySources;
import com.linrun.trigger.config.AgentDeepRuntimeProperties;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

public final class AgentResearchContextPolicy {

    private static final List<String> STYLE_MEMORY_TYPES = List.of("output_style", "business_context");

    private AgentResearchContextPolicy() {
    }

    public static boolean isResearchQuestion(String question, List<String> keywords) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        List<String> effectiveKeywords = keywords == null || keywords.isEmpty()
                ? List.of("论文", "综述", "文献", "survey")
                : keywords;
        for (String keyword : effectiveKeywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (lower.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldInjectMemory(UserAgentMemory memory,
                                             String question,
                                             AgentDeepRuntimeProperties properties) {
        if (memory == null || !Boolean.TRUE.equals(memory.getEnabled())) {
            return false;
        }
        String source = memory.getSource() == null ? UserAgentMemorySources.MANUAL : memory.getSource();
        if (UserAgentMemorySources.AUTO.equals(source)) {
            return properties != null && properties.isInjectAutoMemory();
        }
        if (isResearchQuestion(question, properties == null ? List.of() : properties.getResearchKeywords())) {
            String memoryType = memory.getMemoryType() == null ? "" : memory.getMemoryType();
            if (STYLE_MEMORY_TYPES.contains(memoryType)) {
                return false;
            }
        }
        return true;
    }

    public static String researchPlanningHint(String question, AgentDeepRuntimeProperties properties) {
        if (!isResearchQuestion(question, properties == null ? List.of() : properties.getResearchKeywords())) {
            return "";
        }
        return """
                
                ## 学术/文献调研规划约束
                1. web_search 查询应包含会议/期刊站点提示，例如 site:arxiv.org、site:ieee.org，并带上年份范围。
                2. 每个检索步骤必须整理真实 url 和 title；没有 url 的结果标记为 insufficient_evidence，不得编造 DOI 或准确率。
                3. 不要写“写总结”步骤；总结由系统统一生成。
                """;
    }
}
