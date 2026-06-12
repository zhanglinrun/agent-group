package com.linrun.domain.academic.runtime.evaluation;

/**
 * 一条评测用例：一个学术任务问题，以及它应当被路由到的执行模式。
 *
 * @param caseId               用例编号
 * @param question             用户问题
 * @param attachmentType       附件类型（file / image），无附件传空
 * @param expectedAgentType    期望选中的 Agent 类型（chat / deep / ppt / search / skill / file / image）
 * @param expectedExecutionMode 期望选中的执行模式（ReAct / Plan-Execute / Flow / Skill-SOP）
 * @param simulateStepFailure  是否在计划执行中注入一次步骤失败，用来评测重规划恢复能力
 * @param failureNote          注入失败时的失败说明，决定重规划策略走哪条分支
 */
public record AgentEvalCase(String caseId,
                            String question,
                            String attachmentType,
                            String expectedAgentType,
                            String expectedExecutionMode,
                            boolean simulateStepFailure,
                            String failureNote) {

    public AgentEvalCase {
        caseId = safe(caseId);
        question = safe(question);
        attachmentType = safe(attachmentType);
        expectedAgentType = safe(expectedAgentType);
        expectedExecutionMode = safe(expectedExecutionMode);
        failureNote = safe(failureNote);
    }

    public boolean hasAttachment() {
        return !attachmentType.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
