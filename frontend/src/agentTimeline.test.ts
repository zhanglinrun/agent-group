import { describe, expect, it } from "vitest";

import {
  isTimelineAttentionItem,
  mergeTimelineEvent,
  normalizeTimelineStatus,
  replayEventsToTimeline,
  streamEventToTimelineItem,
  timelineItemStatus,
  timelineItemStatusLabel
} from "./agentTimeline";

describe("agent timeline projection", () => {
  it("normalizes timeline status for display and summaries", () => {
    expect(normalizeTimelineStatus("IN_PROGRESS")).toBe("running");
    expect(normalizeTimelineStatus("SUCCESS")).toBe("completed");
    expect(normalizeTimelineStatus("FAILED")).toBe("error");
    expect(normalizeTimelineStatus("REPLANNED")).toBe("replanned");
  });

  it("marks run start as running and exposes a readable label", () => {
    const item = streamEventToTimelineItem({
      event: "run_start",
      data: { taskType: "data", model: "qwen-plus" }
    });

    expect(item).toMatchObject({
      type: "run",
      status: "running",
      title: "运行开始"
    });
    expect(timelineItemStatus(item)).toBe("running");
    expect(timelineItemStatusLabel(item)).toBe("运行中");
  });

  it("merges run start and run done into one lifecycle item", () => {
    const started = streamEventToTimelineItem({
      event: "run_start",
      data: { taskType: "chat" }
    });
    const done = streamEventToTimelineItem({
      event: "run_done",
      data: { durationMillis: 120 }
    });

    const timeline = mergeTimelineEvent(mergeTimelineEvent([], started), done);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({
      type: "run",
      status: "completed",
      title: "运行完成",
      content: "120 ms"
    });
    expect(timelineItemStatusLabel(timeline[0])).toBe("运行完成");
  });

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

  it("merges tool call and result by tool call id when invocation id is missing", () => {
    const started = streamEventToTimelineItem({
      event: "tool_call",
      data: { invocationId: "tool-1", toolCallId: "call-1", toolName: "report_tool", action: "write report" }
    });
    const completed = streamEventToTimelineItem({
      event: "tool_result",
      data: { toolCallId: "call-1", toolName: "report_tool", resultSummary: "report done", latencyMillis: 20 }
    });

    const timeline = mergeTimelineEvent(mergeTimelineEvent([], started), completed);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({
      type: "tool",
      invocationId: "tool-1",
      toolCallId: "call-1",
      toolName: "report_tool",
      status: "completed",
      detail: "report done"
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
    expect(timelineItemStatusLabel(replanned)).toBe("已重规划");
  });

  it("detects attention items from failed run and tool events", () => {
    expect(isTimelineAttentionItem(streamEventToTimelineItem({
      event: "run_error",
      data: { message: "处理失败" }
    }))).toBe(true);

    expect(isTimelineAttentionItem(streamEventToTimelineItem({
      event: "tool_result",
      data: { invocationId: "tool-2", toolName: "report", status: "FAILED" }
    }))).toBe(true);
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

  it("projects academic project context into timeline", () => {
    const item = streamEventToTimelineItem({
      event: "project_context",
      data: {
        title: "AMR Paper",
        researchQuestion: "Open-set recognition",
        targetVenue: "TWC",
        fileCount: 2,
        pendingPatchCount: 1
      }
    });

    expect(item).toMatchObject({
      type: "project",
      status: "completed",
      title: "AMR Paper",
      content: "研究问题：Open-set recognition 路 目标：TWC 路 材料 2 份 路 待确认补丁 1 个"
    });
  });

  it("projects quota and usage events into timeline items", () => {
    expect(streamEventToTimelineItem({
      event: "quota_delta",
      data: { quotaBalance: 88, usedQuota: 12, frozenQuota: 3 }
    })).toMatchObject({
      type: "tool",
      status: "completed",
      toolName: "额度账户",
      detail: "余额 88 · 已用 12 · 冻结 3"
    });

    expect(streamEventToTimelineItem({
      event: "usage_metric",
      data: { consumedQuota: 2.5, remainingQuota: 85.5, modelName: "qwen-plus" }
    })).toMatchObject({
      type: "tool",
      status: "completed",
      toolName: "额度消耗",
      detail: "本次 2.5 · 剩余 85.5 · qwen-plus"
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

  it("replays tool events merged by tool call id", () => {
    const timeline = replayEventsToTimeline([
      {
        events: [
          { event: "tool_call", data: { invocationId: "tool-1", toolCallId: "call-1", toolName: "web_fetch" } },
          { event: "tool_result", data: { toolCallId: "call-1", toolName: "web_fetch", resultSummary: "ok" } }
        ]
      }
    ]);

    expect(timeline).toHaveLength(1);
    expect(timeline[0]).toMatchObject({ toolCallId: "call-1", status: "completed", detail: "ok" });
  });

  it("replays quota and usage events from persisted runs", () => {
    const timeline = replayEventsToTimeline([
      {
        events: [
          { event: "quota_delta", data: { quotaBalance: 100, usedQuota: 10 } },
          { event: "usage_metric", data: { consumedQuota: 1.5, remainingQuota: 98.5 } }
        ]
      }
    ]);

    expect(timeline).toEqual([
      expect.objectContaining({ toolName: "额度账户", detail: "余额 100 · 已用 10" }),
      expect.objectContaining({ toolName: "额度消耗", detail: "本次 1.5 · 剩余 98.5" })
    ]);
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
