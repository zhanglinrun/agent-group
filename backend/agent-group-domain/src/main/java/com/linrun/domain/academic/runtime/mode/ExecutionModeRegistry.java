package com.linrun.domain.academic.runtime.mode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 执行策略注册表。
 * 只把 ReAct、Plan-Execute 等 Agent 架构作为模式族；PPT 与 Skill 归为业务编排策略。
 */
public class ExecutionModeRegistry {

    private final Map<String, AgentExecutionMode> modes = new HashMap<>();

    /**
     * 注册执行模式
     */
    public void register(AgentExecutionMode mode) {
        if (mode == null || mode.modeName() == null) {
            throw new IllegalArgumentException("Mode and mode name cannot be null");
        }
        modes.put(normalizeModeName(mode.modeName()), mode);
    }

    /**
     * 根据名称获取模式
     */
    public Optional<AgentExecutionMode> getMode(String modeName) {
        return Optional.ofNullable(modes.get(normalizeModeName(modeName)));
    }

    /**
     * 自动选择最合适的执行模式
     */
    public AgentExecutionMode selectMode(AgentExecutionMode.ExecutionContext context) {
        return modes.values().stream()
                .filter(mode -> mode.canHandle(context))
                .max((m1, m2) -> Integer.compare(m1.priority(), m2.priority()))
                .orElse(getDefaultMode());
    }

    /**
     * 获取默认模式（ReAct）
     */
    private AgentExecutionMode getDefaultMode() {
        return getMode("react")
                .or(() -> modes.values().stream().findFirst())
                .orElse(null);
    }

    /**
     * 获取所有已注册的模式名称
     */
    public java.util.Set<String> getRegisteredModeNames() {
        return modes.keySet();
    }

    private String normalizeModeName(String modeName) {
        String normalized = modeName == null ? "" : modeName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "deep", "research" -> "plan-execute";
            case "ppt", "flow", "ppt-workflow" -> "ppt-workflow";
            case "skill", "skills", "manual-skills", "skill-sop", "skill-orchestration" -> "skill-orchestration";
            default -> normalized;
        };
    }

    /**
     * 清空所有注册的模式
     */
    public void clear() {
        modes.clear();
    }
}













