package com.linrun.trigger.agent.entity.record;

import java.util.List;

/**
 * 任务执行结果
 */
public record TaskResult(
        String taskId,
        boolean success,
        String output,
        String error,
        List<SearchResult> sources
) {

    public TaskResult(String taskId, boolean success, String output, String error) {
        this(taskId, success, output, error, List.of());
    }
}
