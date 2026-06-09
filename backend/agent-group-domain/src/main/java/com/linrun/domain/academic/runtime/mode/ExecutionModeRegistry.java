package com.linrun.domain.academic.runtime.mode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 执行模式注册行
 * 管理所有可用的执行模式，支持模式注册、查询和自动选择
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
        modes.put(mode.modeName(), mode);
    }

    /**
     * 根数据名称获取模式
     */
    public Optional<AgentExecutionMode> getMode(String modeName) {
        return Optional.ofNullable(modes.get(modeName));
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
        return modes.get("react");
    }

    /**
     * 获取所有已注册的模式名�?
     */
    public java.util.Set<String> getRegisteredModeNames() {
        return modes.keySet();
    }

    /**
     * 清空所有注册的模式
     */
    public void clear() {
        modes.clear();
    }
}















