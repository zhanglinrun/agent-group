import { describe, expect, it } from "vitest";

import { AGENT_MODES, agentModeById } from "./agentModes";

describe("agent mode model", () => {
  it("keeps core multi-agent execution families visible", () => {
    expect(agentModeById("chat").executionFamily).toBe("react");
    expect(agentModeById("deep").executionFamily).toBe("plan-execute");
    expect(agentModeById("ppt").executionFamily).toBe("flow");
    expect(agentModeById("trade-audit").executionFamily).toBe("flow");
    expect(agentModeById("manual-skills").executionFamily).toBe("skill-sop");
  });

  it("keeps every selector option displayable", () => {
    expect(AGENT_MODES.length).toBeGreaterThanOrEqual(9);
    for (const agent of AGENT_MODES) {
      expect(agent.id).toBeTruthy();
      expect(agent.name).toBeTruthy();
      expect(agent.icon).toBeTruthy();
      expect(agent.executionMode).toBeTruthy();
      expect(agent.summary.length).toBeGreaterThan(8);
    }
  });
});
