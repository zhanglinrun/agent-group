package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.agent.AgentFlowProjector;
import com.linrun.domain.agent.runtime.agent.AgentFlowStage;
import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleResult;
import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleService;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.integer;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentPlanningToolRuntime {

    private static final String COMMAND_CREATE = "create";
    private static final String COMMAND_UPDATE = "update";
    private static final String COMMAND_MARK_STEP = "mark_step";
    private static final String COMMAND_FINISH = "finish";
    private static final String COMMAND_STATUS = "status";
    private static final String COMMAND_FLOW = "flow";

    private final AgentPlanLifecycleService lifecycleService;
    private final AgentFlowProjector flowProjector;
    private AgentPlan currentPlan;

    public AgentPlanningToolRuntime() {
        this(new AgentPlanLifecycleService(), new AgentFlowProjector());
    }

    public AgentPlanningToolRuntime(AgentPlanLifecycleService lifecycleService,
                                       AgentFlowProjector flowProjector) {
        this.lifecycleService = lifecycleService == null ? new AgentPlanLifecycleService() : lifecycleService;
        this.flowProjector = flowProjector == null ? new AgentFlowProjector() : flowProjector;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.PLANNING)
                .description("Create, update, mark, finish, and inspect an executable agent plan.")
                .category("planning")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "create, update, mark_step, finish, status, or flow."),
                                "title", Map.of("type", "string", "description", "Plan title."),
                                "steps", Map.of("type", "array", "description", "Plan step instructions."),
                                "stepIndex", Map.of("type", "integer", "description", "Zero-based step index."),
                                "status", Map.of("type", "string", "description", "not_started, in_progress, completed, or blocked."),
                                "note", Map.of("type", "string", "description", "Optional step note.")),
                        "required", List.of("command")))
                .requiredArguments(List.of("command"))
                .enabled(true)
                .build();
    }

    public synchronized AgentToolStructuredOutput call(AgentToolCallCommand command) {
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String operation = text(arguments.get("command"));
        AgentPlanLifecycleResult result = switch (operation) {
            case COMMAND_CREATE -> create(arguments);
            case COMMAND_UPDATE -> update(arguments);
            case COMMAND_MARK_STEP -> markStep(arguments);
            case COMMAND_FINISH -> finish();
            case COMMAND_STATUS, COMMAND_FLOW -> snapshot();
            default -> throw new AppException("PLAN_0001", "unknown planning command: " + operation);
        };
        if (!COMMAND_STATUS.equals(operation) && !COMMAND_FLOW.equals(operation)) {
            currentPlan = result.getPlan();
        }
        return output(operation, result);
    }

    public synchronized AgentPlan currentPlan() {
        return currentPlan == null ? null : currentPlan.copy();
    }

    private AgentPlanLifecycleResult create(Map<String, Object> arguments) {
        return lifecycleService.create(text(arguments.get("title")), stringList(arguments.get("steps")));
    }

    private AgentPlanLifecycleResult update(Map<String, Object> arguments) {
        requireCurrentPlan();
        return lifecycleService.updateRemaining(currentPlan, text(arguments.get("title")), stringList(arguments.get("steps")));
    }

    private AgentPlanLifecycleResult markStep(Map<String, Object> arguments) {
        requireCurrentPlan();
        int stepIndex = integer(arguments.get("stepIndex"), integer(arguments.get("step_index"), -1));
        return lifecycleService.markStep(currentPlan, stepIndex, text(arguments.get("status")), text(arguments.get("note")));
    }

    private AgentPlanLifecycleResult finish() {
        return lifecycleService.finish(currentPlan);
    }

    private AgentPlanLifecycleResult snapshot() {
        requireCurrentPlan();
        return lifecycleService.ensureExecutable(currentPlan);
    }

    private AgentToolStructuredOutput output(String command, AgentPlanLifecycleResult result) {
        AgentPlan plan = result.getPlan();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", command);
        metadata.put("title", plan == null ? "" : plan.getTitle());
        metadata.put("currentStepIndex", result.getCurrentStepIndex());
        metadata.put("autoAdvanced", result.isAutoAdvanced());
        metadata.put("autoFinished", result.isAutoFinished());
        metadata.put("steps", plan == null ? List.of() : plan.getSteps().stream().map(this::stepMap).toList());
        metadata.put("flowStages", plan == null ? List.of() : flowProjector.buildRemainingStages(plan).stream()
                .map(this::stageMap)
                .toList());
        String currentStep = result.getCurrentStep() == null ? "" : result.getCurrentStep().getInstruction();
        return AgentToolStructuredOutput.builder(AgentToolOutputNames.PLANNING)
                .title(plan == null ? "" : plan.getTitle())
                .summary(summary(command, currentStep, result.isAutoFinished()))
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> stepMap(AgentPlanStep step) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepId", step.getStepId());
        map.put("instruction", step.getInstruction());
        map.put("order", step.getOrder());
        map.put("status", step.getStatus());
        map.put("note", step.getNote());
        map.put("assignedAgent", step.getAssignedAgent());
        map.put("dependencies", step.getDependencies());
        return map;
    }

    private Map<String, Object> stageMap(AgentFlowStage stage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stageIndex", stage.getStageIndex());
        map.put("stepIds", stage.stepIds());
        map.put("steps", stage.getSteps().stream().map(this::stepMap).toList());
        return map;
    }

    private String summary(String command, String currentStep, boolean finished) {
        if (finished) {
            return "plan finished";
        }
        String stepText = firstPresent(currentStep);
        if (!stepText.isEmpty()) {
            return command + ": current step is " + stepText;
        }
        return command + ": no executable step";
    }

    private void requireCurrentPlan() {
        if (currentPlan == null) {
            throw new AppException("PLAN_0002", "plan does not exist");
        }
    }
}















