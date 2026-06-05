import { describe, expect, it } from "vitest";

import {
  mergeTimelineEvent,
  replayEventsToTimeline,
  streamEventToTimelineItem
} from "./agentTimeline";

describe("agent timeline projection", () => {
  it("merges tool call and result by invocation id", () => {
    const started = streamEventToTimelineItem({
      event: "tool_call",
      data: { invocationId: "tool-1", toolName: "web_fetch", action: "fetch page" }
    });
    const completed = streamEventToTimelineItem({
      event: "tool_result",
      data: { invocationId: "tool-1", toolName: "web_fetch", resultSummary: "done", latencyMillis: 15 }
    });

    const timeline = mergeTimelineEvent(mergeTimelineEvent([], started), completed);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({
      type: "tool",
      invocationId: "tool-1",
      toolName: "web_fetch",
      status: "completed",
      detail: "done"
    });
  });

  it("updates flow stages by stage index", () => {
    const running = streamEventToTimelineItem({
      event: "flow_delta",
      data: { stageIndex: 1, status: "RUNNING", message: "planning" }
    });
    const completed = streamEventToTimelineItem({
      event: "flow_delta",
      data: { stageIndex: 1, status: "COMPLETED", message: "planned" }
    });

    const timeline = mergeTimelineEvent(mergeTimelineEvent([], running), completed);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({
      type: "flow",
      stageIndex: 1,
      status: "completed",
      message: "planned"
    });
  });

  it("keeps replanned flow status in the timeline", () => {
    const replanned = streamEventToTimelineItem({
      event: "flow_delta",
      data: {
        stageIndex: 0,
        status: "REPLANNED",
        message: "计划已重规划：改查额度流水",
        steps: [{ stepId: "R1", instruction: "查询额度流水" }]
      }
    });

    expect(replanned).toMatchObject({
      type: "flow",
      stageIndex: 0,
      status: "replanned",
      message: "计划已重规划：改查额度流水"
    });
  });

  it("keeps plan revision and replan reason from plan events", () => {
    const plan = streamEventToTimelineItem({
      event: "plan_delta",
      data: {
        title: "重规划计划",
        revision: 2,
        changeType: "replan",
        replanReason: "网页资料不足，改查额度流水",
        steps: ["改查额度流水"]
      }
    });

    expect(plan).toMatchObject({
      type: "plan",
      title: "重规划计划",
      revision: 2,
      changeType: "replan",
      replanReason: "网页资料不足，改查额度流水"
    });
  });

  it("replays events through the same merge rules", () => {
    const timeline = replayEventsToTimeline([
      {
        events: [
          { event: "tool_call", data: { invocationId: "tool-1", toolName: "web_fetch" } },
          { event: "tool_result", data: { invocationId: "tool-1", toolName: "web_fetch", resultSummary: "ok" } }
        ]
      }
    ]);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({ status: "completed", detail: "ok" });
  });

  it("does not merge flow stages across plan versions", () => {
    const timeline = replayEventsToTimeline([
      {
        events: [
          { event: "plan_delta", data: { title: "初始计划", steps: ["先查订单"] } },
          { event: "flow_delta", data: { stageIndex: 0, status: "RUNNING", message: "start" } },
          { event: "flow_delta", data: { stageIndex: 0, status: "REPLANNED", message: "计划已重规划" } },
          { event: "plan_delta", data: { title: "重规划计划", steps: ["改查额度流水"] } },
          { event: "flow_delta", data: { stageIndex: 0, status: "COMPLETED", message: "done" } }
        ]
      }
    ]);

    expect(timeline.filter((item) => item.type === "plan")).toHaveLength(2);
    expect(timeline.filter((item) => item.type === "flow")).toEqual([
      expect.objectContaining({ status: "replanned" }),
      expect.objectContaining({ status: "completed" })
    ]);
  });
});
