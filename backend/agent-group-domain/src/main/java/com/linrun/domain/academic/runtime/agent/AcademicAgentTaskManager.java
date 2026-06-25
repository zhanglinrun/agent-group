package com.linrun.domain.academic.runtime.agent;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领域运行时的轻量任务管理器。
 *
 * 仅维护 domain 侧 Agent 运行时的会话级 sink 生命周期，区别于 trigger 侧基于 Redis 的 AgentTaskManager。
 */
@Component
public class AcademicAgentTaskManager {

    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    public boolean hasRunningTask(String sessionId) {
        return taskMap.containsKey(sessionId);
    }

    public TaskInfo registerTask(String sessionId, Sinks.Many<String> sink, String agentType) {
        TaskInfo taskInfo = new TaskInfo(sink, agentType);
        return taskMap.putIfAbsent(sessionId, taskInfo) == null ? taskInfo : null;
    }

    public boolean stopTask(String sessionId) {
        TaskInfo taskInfo = taskMap.remove(sessionId);
        if (taskInfo == null) {
            return false;
        }
        taskInfo.getSink().tryEmitComplete();
        return true;
    }

    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        private final String agentType;
        private final long createTime;

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
        }

        public Sinks.Many<String> getSink() {
            return sink;
        }

        public String getAgentType() {
            return agentType;
        }

        public long getCreateTime() {
            return createTime;
        }
    }
}
