import { describe, expect, it } from "vitest";

import { buildPlannerHistory } from "./plannerHistory";

describe("planner history projection", () => {
  it("creates plan versions from plan timeline items", () => {
    const history = buildPlannerHistory([
      {
        type: "plan",
        title: "初始计划",
        steps: [
          { stepId: "S1", instruction: "查询订单" },
          { stepId: "S2", instruction: "核对额度" }
        ]
      },
      { type: "flow", stageIndex: 0, status: "RUNNING" }
    ]);

    expect(history).toEqual([
      expect.objectContaining({
        id: "plan-1",
        title: "初始计划",
        stepCount: 2,
        flowUpdates: 1,
        status: "running",
        latest: true
      })
    ]);
  });

  it("keeps replanned versions and marks latest", () => {
    const history = buildPlannerHistory([
      { type: "plan", title: "初始计划", steps: ["先查订单"] },
      { type: "flow", stageIndex: 0, status: "REPLANNED" },
      { type: "plan", title: "重规划计划", steps: ["改查退款"] },
      { type: "flow", stageIndex: 0, status: "COMPLETED" }
    ]);

    expect(history).toHaveLength(2);
    expect(history[0]).toMatchObject({ status: "replanned", latest: false });
    expect(history[1]).toMatchObject({ status: "completed", latest: true });
  });

  it("counts staged plan steps", () => {
    const history = buildPlannerHistory([
      {
        type: "plan",
        title: "分阶段计划",
        flowStages: [
          { stageIndex: 0, steps: [{ instruction: "A" }, { instruction: "B" }] },
          { stageIndex: 1, steps: [{ instruction: "C" }] }
        ]
      }
    ]);

    expect(history[0]).toMatchObject({
      stageCount: 2,
      stepCount: 3,
      summary: "A 等 3 步"
    });
  });
});
