package com.linrun.domain.academic.runtime.agent.ppt;

import org.springframework.stereotype.Component;

/**
 * PPT 意图识别器
 *
 * 根据用户输入识别用户意图：创建、修改、恢复
 */
@Component
public class PptIntentRecognizer {

    /**
     * 识别用户意图
     *
     * @param sessionId 会话 ID
     * @param query 用户输入
     * @return 意图识别结果
     */
    public PptIntentResult recognize(String sessionId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new PptIntentResult(PptIntent.UNKNOWN, "输入为空");
        }

        String normalizedQuery = query.toLowerCase().trim();

        // 检查是否是创建意图
        if (isCreateIntent(normalizedQuery)) {
            return new PptIntentResult(PptIntent.CREATE_PPT, "检测到创建PPT关键词");
        }

        // 检查是否是修改意图
        if (isModifyIntent(normalizedQuery)) {
            return new PptIntentResult(PptIntent.MODIFY_PPT, "检测到修改PPT关键词");
        }

        // 检查是否是恢复意图
        if (isResumeIntent(normalizedQuery)) {
            return new PptIntentResult(PptIntent.RESUME_PPT, "检测到继续/恢复关键词");
        }

        // 默认视为创建意图（如果提到 PPT 但未明确修改/恢复）
        if (normalizedQuery.contains("ppt") || normalizedQuery.contains("幻灯片") ||
            normalizedQuery.contains("演示文稿")) {
            return new PptIntentResult(PptIntent.CREATE_PPT, "包含PPT相关词汇，默认为创建");
        }

        return new PptIntentResult(PptIntent.UNKNOWN, "无法识别意图");
    }

    private boolean isCreateIntent(String query) {
        String[] createKeywords = {
            "生成", "创建", "制作", "做一个", "做个", "帮我做",
            "新建", "设计", "准备", "编写"
        };

        for (String keyword : createKeywords) {
            if (query.contains(keyword) &&
                (query.contains("ppt") || query.contains("幻灯片") || query.contains("演示"))) {
                return true;
            }
        }

        return false;
    }

    private boolean isModifyIntent(String query) {
        String[] modifyKeywords = {
            "修改", "改一下", "调整", "优化", "更新", "完善",
            "增加", "删除", "替换", "改成", "换成"
        };

        for (String keyword : modifyKeywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isResumeIntent(String query) {
        String[] resumeKeywords = {
            "继续", "恢复", "接着", "上次", "刚才", "之前的"
        };

        for (String keyword : resumeKeywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 意图识别结果
     */
    public static class PptIntentResult {
        private final PptIntent intent;
        private final String reason;

        public PptIntentResult(PptIntent intent, String reason) {
            this.intent = intent;
            this.reason = reason;
        }

        public PptIntent getIntent() {
            return intent;
        }

        public String getReason() {
            return reason;
        }
    }
}
