package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.domain.agent.ledger.service.AgentLedgerContext;
import com.linrun.domain.support.trace.TraceContext;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import com.linrun.trigger.agent.entity.OverAllState;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次 deep 模式运行中的工程上下文。
 * 第一阶段只承载 Graph 最小闭环所需信息，后续再扩展 skill、memory 和 A2A。
 */
public class AgentRunContext {

    private final String userId;
    private final String sessionId;
    private final String runId;
    private final String requestId;
    private final String traceId;
    private final String spanId;
    private final String mode;
    private final String taskType;
    private final OverAllState state;
    private final Sinks.Many<String> sink;
    private final AtomicBoolean finished;
    private final StringBuilder thinkingBuffer;
    private AgentMemorySnapshot memorySnapshot;
    private List<SkillRuntimeDescriptor> availableSkills = List.of();
    private List<PlanTask> currentPlan = List.of();
    private Map<String, TaskResult> currentResults = new LinkedHashMap<>();
    private boolean reviewPassed;
    private String reviewFeedback = "";

    private AgentRunContext(Builder builder) {
        this.userId = blank(builder.userId);
        this.sessionId = blank(builder.sessionId);
        this.runId = blank(builder.runId);
        this.requestId = blank(builder.requestId);
        this.traceId = blank(builder.traceId);
        this.spanId = blank(builder.spanId);
        this.mode = StringUtils.hasText(builder.mode) ? builder.mode.trim() : "deep";
        this.taskType = StringUtils.hasText(builder.taskType) ? builder.taskType.trim() : this.mode;
        this.state = builder.state;
        this.sink = builder.sink;
        this.finished = builder.finished;
        this.thinkingBuffer = builder.thinkingBuffer;
        this.memorySnapshot = builder.memorySnapshot == null
                ? AgentMemorySnapshot.empty(this.userId, this.sessionId)
                : builder.memorySnapshot;
        this.availableSkills = builder.availableSkills == null ? List.of() : List.copyOf(builder.availableSkills);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentRunContext fromCurrent(OverAllState state,
                                              Sinks.Many<String> sink,
                                              AtomicBoolean finished,
                                              StringBuilder thinkingBuffer) {
        AgentLedgerContext.Context ledger = AgentLedgerContext.current();
        TraceContext.TraceSnapshot trace = TraceContext.snapshot();
        return builder()
                .userId(ledger == null ? "" : ledger.userId())
                .sessionId(ledger == null ? "" : ledger.sessionId())
                .runId(ledger == null ? "" : ledger.runId())
                .requestId(ledger == null ? "" : ledger.requestId())
                .traceId(trace == null ? "" : trace.getTraceId())
                .spanId(trace == null ? "" : trace.getSpanId())
                .mode("deep")
                .taskType(ledger == null ? "deep" : ledger.taskType())
                .state(state)
                .sink(sink)
                .finished(finished)
                .thinkingBuffer(thinkingBuffer)
                .build();
    }

    public String userId() {
        return userId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String runId() {
        return runId;
    }

    public String requestId() {
        return requestId;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String mode() {
        return mode;
    }

    public String taskType() {
        return taskType;
    }

    public OverAllState state() {
        return state;
    }

    public Sinks.Many<String> sink() {
        return sink;
    }

    public AtomicBoolean finished() {
        return finished;
    }

    public StringBuilder thinkingBuffer() {
        return thinkingBuffer;
    }

    public List<SkillRuntimeDescriptor> availableSkills() {
        return availableSkills;
    }

    public void availableSkills(List<SkillRuntimeDescriptor> availableSkills) {
        this.availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
    }

    public List<PlanTask> currentPlan() {
        return currentPlan;
    }

    public void currentPlan(List<PlanTask> currentPlan) {
        this.currentPlan = currentPlan == null ? List.of() : currentPlan;
    }

    public Map<String, TaskResult> currentResults() {
        return currentResults;
    }

    public void currentResults(Map<String, TaskResult> currentResults) {
        this.currentResults = currentResults == null ? new LinkedHashMap<>() : currentResults;
    }

    public boolean reviewPassed() {
        return reviewPassed;
    }

    public void reviewPassed(boolean reviewPassed) {
        this.reviewPassed = reviewPassed;
    }

    public String reviewFeedback() {
        return reviewFeedback;
    }

    public void reviewFeedback(String reviewFeedback) {
        this.reviewFeedback = blank(reviewFeedback);
    }

    public AgentMemorySnapshot memorySnapshot() {
        return memorySnapshot;
    }

    public void memorySnapshot(AgentMemorySnapshot memorySnapshot) {
        this.memorySnapshot = memorySnapshot == null
                ? AgentMemorySnapshot.empty(userId, sessionId)
                : memorySnapshot;
    }

    public AgentRoleContext plannerContext() {
        return AgentRoleContext.builder(AgentRoleContext.Role.PLANNER)
                .put("identity", identity())
                .put("goal", state == null ? "" : state.getQuestion())
                .put("mode", mode)
                .put("taskType", taskType)
                .put("memory", memorySnapshot.evidence())
                .put("shortTermMemory", memorySnapshot.shortTerm())
                .put("taskMemory", memorySnapshot.taskMemory())
                .put("userPreferenceMemory", memorySnapshot.longTerm())
                .put("skillCount", availableSkills.size())
                .put("availableSkillNames", availableSkills.stream().map(SkillRuntimeDescriptor::name).toList())
                .build();
    }

    public AgentRoleContext workerContext(List<String> registeredTools) {
        return AgentRoleContext.builder(AgentRoleContext.Role.WORKER)
                .put("identity", identity())
                .put("currentPlan", currentPlan.stream().map(PlanTask::instruction).toList())
                .put("shortTermMemory", memorySnapshot.shortTerm())
                .put("taskMemory", memorySnapshot.taskMemory())
                .put("userPreferenceMemory", memorySnapshot.longTerm())
                .put("skillCount", availableSkills.size())
                .put("skills", availableSkills.stream()
                        .map(skill -> skill.toAuditMap(registeredTools == null ? java.util.Set.of() : java.util.Set.copyOf(registeredTools)))
                        .toList())
                .put("registeredTools", registeredTools == null ? List.of() : List.copyOf(registeredTools))
                .build();
    }

    public AgentRoleContext reviewerContext() {
        return AgentRoleContext.builder(AgentRoleContext.Role.REVIEWER)
                .put("identity", identity())
                .put("planCount", currentPlan.size())
                .put("resultCount", currentResults.size())
                .put("taskMemory", memorySnapshot.taskMemory())
                .put("userPreferenceMemory", memorySnapshot.longTerm())
                .put("failedTasks", currentResults.values().stream()
                        .filter(result -> result != null && !result.success())
                        .map(TaskResult::taskId)
                        .toList())
                .build();
    }

    public Map<String, Object> contextEvidence(List<String> registeredTools) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planner", plannerContext().includedSections());
        data.put("worker", workerContext(registeredTools).includedSections());
        data.put("reviewer", reviewerContext().includedSections());
        return data;
    }

    private Map<String, Object> identity() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("sessionId", sessionId);
        data.put("runId", runId);
        data.put("requestId", requestId);
        data.put("traceId", traceId);
        data.put("spanId", spanId);
        return data;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public static class Builder {
        private String userId;
        private String sessionId;
        private String runId;
        private String requestId;
        private String traceId;
        private String spanId;
        private String mode;
        private String taskType;
        private OverAllState state;
        private Sinks.Many<String> sink;
        private AtomicBoolean finished;
        private StringBuilder thinkingBuffer;
        private AgentMemorySnapshot memorySnapshot;
        private List<SkillRuntimeDescriptor> availableSkills = List.of();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder state(OverAllState state) {
            this.state = state;
            return this;
        }

        public Builder sink(Sinks.Many<String> sink) {
            this.sink = sink;
            return this;
        }

        public Builder finished(AtomicBoolean finished) {
            this.finished = finished;
            return this;
        }

        public Builder thinkingBuffer(StringBuilder thinkingBuffer) {
            this.thinkingBuffer = thinkingBuffer;
            return this;
        }

        public Builder memorySnapshot(AgentMemorySnapshot memorySnapshot) {
            this.memorySnapshot = memorySnapshot;
            return this;
        }

        public Builder availableSkills(List<SkillRuntimeDescriptor> availableSkills) {
            this.availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
            return this;
        }

        public AgentRunContext build() {
            return new AgentRunContext(this);
        }
    }
}
