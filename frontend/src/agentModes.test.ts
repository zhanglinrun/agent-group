import { describe, expect, it } from "vitest";

import { AGENT_MODES, USER_AGENT_MODES, agentModeById } from "./agentModes";

describe("agent mode model", () => {
  it("keeps core execution strategies visible", () => {
    expect(agentModeById("chat").executionFamily).toBe("react");
    expect(agentModeById("deep").executionFamily).toBe("plan-execute");
    expect(agentModeById("deep").executionMode).toBe("Plan-Execute");
    expect(agentModeById("deep").replanEnabled).toBe(true);
    expect(agentModeById("deep").replanLabel).toBe("重规划");
    expect(agentModeById("ppt").executionFamily).toBe("ppt-workflow");
    expect(agentModeById("manual-skills").executionFamily).toBe("skill-orchestration");
    expect(agentModeById("manual-skills").name).toBe("Skill");
  });

  it("keeps the user selector focused on one chat surface", () => {
    expect(USER_AGENT_MODES.map((agent) => agent.id)).toEqual(["auto", "chat", "ppt", "deep", "image", "manual-skills"]);
  });

  it("keeps every selector option displayable", () => {
    expect(AGENT_MODES.length).toBeGreaterThanOrEqual(8);
    for (const agent of AGENT_MODES) {
      expect(agent.id).toBeTruthy();
      expect(agent.name).toBeTruthy();
      expect(agent.icon).toBeTruthy();
      expect(agent.executionMode).toBeTruthy();
      expect(agent.summary.length).toBeGreaterThan(8);
    }
  });
});
