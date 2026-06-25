package com.linrun.domain.academic.runtime.agent.ppt;

/**
 * PPT 意图类型
 */
public enum PptIntent {
    /**
     * 创建新 PPT
     */
    CREATE_PPT("创建PPT"),

    /**
     * 修改现有 PPT
     */
    MODIFY_PPT("修改PPT"),

    /**
     * 恢复/继续 PPT
     */
    RESUME_PPT("恢复PPT"),

    /**
     * 无法识别的意图
     */
    UNKNOWN("未知");

    private final String description;

    PptIntent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
