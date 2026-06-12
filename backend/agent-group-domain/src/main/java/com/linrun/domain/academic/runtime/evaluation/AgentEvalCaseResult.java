package com.linrun.domain.academic.runtime.evaluation;

/**
 * 单条用例的评测结果。
 */
public record AgentEvalCaseResult(String caseId,
                                  String question,
                                  String expectedAgentType,
                                  String actualAgentType,
                                  String expectedExecutionMode,
                                  String actualExecutionMode,
                                  String taskType,
                                  boolean modeCorrect,
                                  boolean flowCompleted,
                                  int planSteps,
                                  int replanCount,
                                  long elapsedMillis) {
}
