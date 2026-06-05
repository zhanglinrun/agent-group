import { describe, expect, it } from "vitest";

import { AGENT_MODES } from "./agentModes";
import { buildAgentExecutionSummary } from "./agentExecutionSummary";

describe("agent execution summary", () => {
  it("summarizes local agent modes as complete multi-agent coverage", () => {
    const summary = buildAgentExecutionSummary(AGENT_MODES);

    expect(summary.status).toBe("ready");
    expect(summary.modeCount).toBe(AGENT_MODES.length);
    expect(summary.coveredFamilyCount).toBe(4);
    expect(summary.missingFamilies).toEqual([]);
    expect(summary.replanModeIds).toEqual(["deep"]);
    expect(summary.metrics).toEqual([
      { key: "families", label: "执行族", value: "4/4", tone: "good" },
      { key: "modes", label: "模式", value: String(AGENT_MODES.length), tone: "good" },
      { key: "replan", label: "重规划", value: "1", tone: "good" }
    ]);
    expect(summary.actions).toEqual(["多智能体执行模式覆盖完整"]);
  });

  it("accepts backend capability field names", () => {
    const summary = buildAgentExecutionSummary([
      { agentId: "chat", family: "react", executionMode: "ReAct" },
      {
        agentId: "deep",
        family: "plan-execute",
        executionMode: "Plan Execute",
        replanEnabled: true,
        replanEvidence: ["flow_delta:REPLANNED"]
      },
      { agentId: "ppt", family: "flow", executionMode: "Flow" },
      { agentId: "skills", family: "skill-sop", executionMode: "Skill + SOP" }
    ]);

    expect(summary.status).toBe("ready");
    expect(summary.replanModeIds).toEqual(["deep"]);
    expect(summary.replanEvidence).toEqual(["flow_delta:REPLANNED"]);
  });

  it("marks partial coverage when families or replan evidence are missing", () => {
    const summary = buildAgentExecutionSummary([
      { id: "chat", executionFamily: "react", executionMode: "ReAct" },
      { id: "ppt", executionFamily: "flow", executionMode: "Flow" }
    ]);

    expect(summary.status).toBe("partial");
    expect(summary.missingFamilies.map((family) => family.key)).toEqual(["plan-execute", "skill-sop"]);
    expect(summary.actions).toEqual([
      "补齐 Plan Execute、Skill SOP 执行模式",
      "接入动态重规划证据"
    ]);
  });
});
