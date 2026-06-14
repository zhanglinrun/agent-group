import { describe, expect, it } from "vitest";

import { buildAgentRunEvidenceSummary } from "./agentRunEvidence";

describe("agent run evidence summary", () => {
  it("hides when backend evidence is absent", () => {
    expect(buildAgentRunEvidenceSummary({ run: { runId: "RUN1" } })).toEqual({
      visible: false,
      metrics: [],
      highlights: []
    });
  });

  it("projects backend run evidence into minimal display metrics", () => {
    const summary = buildAgentRunEvidenceSummary({
      evidence: {
        mode: {
          executionMode: "Plan-Execute",
          modeFamily: "plan-execute",
          reason: "深度研究任务需要多步骤规划和依赖编排"
        },
        plan: {
          title: "深度任务",
          revisionCount: 2,
          steps: [{ stepId: "S1" }, { stepId: "S2" }]
        },
        toolCallCount: 3,
        failedToolCount: 1,
        replanCount: 1,
        llmCallCount: 1,
        artifactCount: 1,
        quotaConsumed: 12.5,
        toolSuccessRate: 0.67,
        diagnosisLevel: "WARN",
        diagnosisSummary: "存在工具失败",
        failedTools: [{ toolName: "code_interpreter", errorMessage: "script timeout" }],
        replanReasons: ["code_interpreter 调用失败：script timeout"]
      }
    });

    expect(summary.visible).toBe(true);
    expect(summary.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: "mode", value: "Plan-Execute" }),
      expect.objectContaining({ key: "plan", value: "2 版 / 2 步" }),
      expect.objectContaining({ key: "tools", value: "2 成功 / 1 异常", tone: "warn" }),
      expect.objectContaining({ key: "replan", value: "1 次", tone: "warn" }),
      expect.objectContaining({ key: "quota", value: "12.5" }),
      expect.objectContaining({ key: "diagnosis", value: "WARN", tone: "warn" })
    ]));
    expect(summary.highlights.join("\n")).toContain("重规划：code_interpreter 调用失败：script timeout");
    expect(summary.highlights.join("\n")).toContain("失败工具：code_interpreter，script timeout");
  });
});
