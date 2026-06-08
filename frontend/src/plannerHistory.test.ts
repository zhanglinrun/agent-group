import { describe, expect, it } from "vitest";

import { buildPlannerHistory } from "./plannerHistory";

describe("planner history projection", () => {
  it("creates plan versions from plan timeline items", () => {
    const history = buildPlannerHistory([
      {
        type: "plan",
        title: "初始计划",
        steps: [
          { stepId: "S1", instruction: "读取论文摘要" },
          { stepId: "S2", instruction: "核对实验指标" }
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
      { type: "plan", title: "初始计划", steps: ["先读论文摘要"] },
      { type: "flow", stageIndex: 0, status: "REPLANNED", message: "引用数据不足，改查实验结果" },
      {
        type: "plan",
        title: "重规划计划",
        revision: 2,
        changeType: "replan",
        replanReason: "引用数据不足，改查实验结果",
        steps: ["改查实验结果"]
      },
      { type: "flow", stageIndex: 0, status: "COMPLETED" }
    ]);

    expect(history).toHaveLength(2);
    expect(history[0]).toMatchObject({
      status: "replanned",
      replanReason: "引用数据不足，改查实验结果",
      latest: false
    });
    expect(history[1]).toMatchObject({
      revision: 2,
      changeType: "replan",
      replanReason: "引用数据不足，改查实验结果",
      status: "completed",
      latest: true
    });
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
