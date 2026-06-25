package com.linrun.trigger.agent.agent.pptx.strategy;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PPT 需求澄清的结构化决策结果。
 *
 * 替代原先依赖中文关键字（如"【开始生成PPT】"）的判定方式，
 * 由 LLM 在需求澄清回复末尾输出结构化 JSON，再用 BeanOutputConverter 反序列化为本枚举。
 */
public record PptContinueDecision(
        @JsonProperty("decision") Decision decision,
        @JsonProperty("summary") String summary
) {

    public enum Decision {
        /** 信息已足够，可进入下一步 */
        CONTINUE,
        /** 信息不足，需暂停并向用户追问 */
        PAUSE
    }

    public boolean shouldContinue() {
        return decision == Decision.CONTINUE;
    }
}
