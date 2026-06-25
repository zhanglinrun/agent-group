package com.linrun.domain.academic.runtime.agent;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 任务管理器
 *
 * 功能：
 * 1. 防止同一会话并发执行多个 Agent 任务
 * 2. 支持任务停止和取消
 * 3. 监控正在运行的任务
 */
@Component("academicStreamTaskManager")
public class AgentTaskManager {

    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();

    /**
     * 检查会话是否有正在运行的任务
     *
     * @param sessionId 会话 ID
     * @return true 表示有任务正在运行
     */
    public boolean hasRunningTask(String sessionId) {
        return runningTasks.containsKey(sessionId);
    }

    /**
     * 注册任务
     *
     * @param sessionId 会话 ID
     * @param sink 流式响应 sink
     * @param agentType Agent 类型
     * @return 任务信息，如果注册失败返回 null
     */
    public TaskInfo registerTask(String sessionId, Sinks.Many<String> sink, String agentType) {
        if (sessionId == null) {
            return null;
        }

        TaskInfo existingTask = runningTasks.get(sessionId);
        if (existingTask != null) {
            return null;
        }

        TaskInfo taskInfo = new TaskInfo(sessionId, sink, agentType, LocalDateTime.now());
        runningTasks.put(sessionId, taskInfo);
        return taskInfo;
    }

    /**
     * 停止任务
     *
     * @param sessionId 会话 ID
     */
    public void stopTask(String sessionId) {
        if (sessionId == null) {
            return;
        }

        TaskInfo taskInfo = runningTasks.remove(sessionId);
        if (taskInfo != null) {
            // 尝试完成 sink（如果还未完成）
            if (taskInfo.sink != null) {
                try {
                    taskInfo.sink.tryEmitComplete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * 取消任务
     *
     * @param sessionId 会话 ID
     * @param reason 取消原因
     */
    public void cancelTask(String sessionId, String reason) {
        if (sessionId == null) {
            return;
        }

        TaskInfo taskInfo = runningTasks.remove(sessionId);
        if (taskInfo != null) {
            // 发送错误并完成 sink
            if (taskInfo.sink != null) {
                try {
                    taskInfo.sink.tryEmitNext(AgentResponse.error(reason));
                    taskInfo.sink.tryEmitComplete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * 获取任务信息
     *
     * @param sessionId 会话 ID
     * @return 任务信息，不存在返回 null
     */
    public TaskInfo getTask(String sessionId) {
        return runningTasks.get(sessionId);
    }

    /**
     * 获取正在运行的任务数量
     *
     * @return 任务数量
     */
    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    /**
     * 清理所有任务（通常用于应用关闭时）
     */
    public void clearAll() {
        runningTasks.forEach((sessionId, taskInfo) -> {
            if (taskInfo.sink != null) {
                try {
                    taskInfo.sink.tryEmitNext(AgentResponse.error("服务关闭，任务终止"));
                    taskInfo.sink.tryEmitComplete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        });
        runningTasks.clear();
    }

    /**
     * 任务信息
     */
    public static class TaskInfo {
        private final String sessionId;
        private final Sinks.Many<String> sink;
        private final String agentType;
        private final LocalDateTime startTime;

        public TaskInfo(String sessionId, Sinks.Many<String> sink, String agentType, LocalDateTime startTime) {
            this.sessionId = sessionId;
            this.sink = sink;
            this.agentType = agentType;
            this.startTime = startTime;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Sinks.Many<String> getSink() {
            return sink;
        }

        public String getAgentType() {
            return agentType;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public long getElapsedMillis() {
            return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        }
    }
}
