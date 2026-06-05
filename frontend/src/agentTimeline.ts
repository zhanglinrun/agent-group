export type TimelineItem = Record<string, unknown> & {
  type: string;
  status?: string;
  invocationId?: string;
  toolCallId?: string;
  stageIndex?: number;
};

export type TimelineStatus = "thinking" | "planned" | "running" | "completed" | "replanned" | "blocked" | "error";

type UnknownMap = Record<string, unknown>;

function asObject(value: unknown): UnknownMap {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as UnknownMap)
    : {};
}

function text(value: unknown): string {
  return String(value ?? "").trim();
}

export function normalizeTimelineStatus(status: unknown, fallback: TimelineStatus = "planned"): TimelineStatus {
  const normalized = text(status).toLowerCase().replace(/[\s-]+/g, "_");
  if (!normalized) {
    return fallback;
  }
  if (["thinking", "reasoning"].includes(normalized)) {
    return "thinking";
  }
  if (["running", "run", "started", "start", "pending", "processing", "in_progress"].includes(normalized)) {
    return "running";
  }
  if (["completed", "complete", "done", "success", "succeeded", "finished", "finish"].includes(normalized)) {
    return "completed";
  }
  if (["replanned", "replan", "re_planned", "re_planning"].includes(normalized)) {
    return "replanned";
  }
  if (["blocked", "timeout", "cancelled", "canceled"].includes(normalized)) {
    return "blocked";
  }
  if (["error", "failed", "fail", "failure"].includes(normalized)) {
    return "error";
  }
  if (["planned", "plan", "created", "ready"].includes(normalized)) {
    return "planned";
  }
  if (normalized.includes("fail") || normalized.includes("error")) {
    return "error";
  }
  return fallback;
}

export function timelineItemStatus(item: unknown): TimelineStatus {
  const entry = asObject(item);
  const type = text(entry.type);
  if (text(entry.status)) {
    return normalizeTimelineStatus(entry.status, type === "thinking" ? "thinking" : "planned");
  }
  if (type === "thinking") return "thinking";
  if (type === "plan") return "planned";
  if (type === "flow" || type === "tool") return "running";
  if (type === "run" || type === "llm") return "completed";
  if (type === "error") return "error";
  return "planned";
}

export function timelineStatusLabel(status: unknown): string {
  return ({
    thinking: "思考中",
    planned: "已规划",
    running: "执行中",
    completed: "已完成",
    replanned: "已重规划",
    blocked: "已阻塞",
    error: "异常"
  } as Record<TimelineStatus, string>)[normalizeTimelineStatus(status)];
}

export function timelineItemStatusLabel(item: unknown): string {
  const entry = asObject(item);
  const status = timelineItemStatus(entry);
  const type = text(entry.type);
  if (type === "run") {
    return status === "running" ? "运行中" : status === "error" ? "运行失败" : "运行完成";
  }
  if (type === "tool") {
    return status === "running" ? "调用中" : status === "error" ? "调用失败" : status === "blocked" ? "调用受阻" : "已返回";
  }
  if (type === "llm") {
    return status === "running" ? "生成中" : status === "error" ? "生成失败" : "生成完成";
  }
  if (type === "plan") {
    return status === "replanned" ? "已重规划" : "已规划";
  }
  return timelineStatusLabel(status);
}

export function isTimelineAttentionItem(item: unknown): boolean {
  const status = timelineItemStatus(item);
  return status === "error" || status === "blocked";
}

function normalizePlanSteps(data: UnknownMap): unknown[] {
  if (Array.isArray(data.structuredSteps) && data.structuredSteps.length) {
    return data.structuredSteps;
  }
  return (Array.isArray(data.steps) ? data.steps : []).map((step, index) => (
    typeof step === "string"
      ? { stepId: `S${index + 1}`, instruction: step, assignedAgent: "", dependencies: [] }
      : step
  ));
}

export function planStepLabel(step: unknown): string {
  const item = asObject(step);
  return typeof step === "string" ? step : (text(item.instruction) || text(item.stepId));
}

export function planStepMeta(step: unknown): string {
  if (typeof step === "string") {
    return "";
  }
  const item = asObject(step);
  const deps = Array.isArray(item.dependencies) && item.dependencies.length
    ? `依赖 ${item.dependencies.join(", ")}`
    : "";
  return [text(item.assignedAgent), deps].filter(Boolean).join(" · ");
}

export function mergeThinking(timeline: TimelineItem[] = [], content: string): TimelineItem[] {
  const last = timeline[timeline.length - 1];
  if (last?.type === "thinking") {
    return [...timeline.slice(0, -1), { ...last, content }];
  }
  return [...timeline, { type: "thinking", content }];
}

export function streamEventToTimelineItem(
  event: unknown,
  normalizeMessage: (message: unknown, fallback?: string) => string = (message, fallback = "处理失败") => text(message) || fallback
): TimelineItem | null {
  const envelope = asObject(event);
  const data = asObject(envelope.data);
  const eventName = text(envelope.event);
  if (eventName === "run_start") {
    return {
      type: "run",
      status: "running",
      title: "运行开始",
      content: `${text(data.taskType) || "chat"} · ${text(data.model) || "bear-doctor-agent"}`
    };
  }
  if (eventName === "plan_delta") {
    return {
      type: "plan",
      status: "planned",
      title: text(data.title) || "执行计划",
      revision: Number(data.revision || data.planRevision || 0),
      changeType: text(data.changeType),
      replanReason: text(data.replanReason),
      steps: normalizePlanSteps(data),
      flowStages: Array.isArray(data.flowStages) ? data.flowStages : []
    };
  }
  if (eventName === "flow_delta") {
    return {
      type: "flow",
      stageIndex: Number(data.stageIndex ?? 0),
      status: normalizeTimelineStatus(data.status, "running"),
      message: text(data.message),
      steps: Array.isArray(data.steps) ? data.steps : []
    };
  }
  if (eventName === "tool_call") {
    return {
      type: "tool",
      invocationId: text(data.invocationId),
      toolCallId: text(data.toolCallId),
      toolName: text(data.toolName) || "工具调用",
      detail: text(data.action) || text(data.argumentsJson),
      status: "running"
    };
  }
  if (eventName === "tool_result") {
    return {
      type: "tool",
      invocationId: text(data.invocationId),
      toolCallId: text(data.toolCallId),
      toolName: text(data.toolName) || "工具调用",
      detail: text(data.resultSummary) || text(data.errorMessage),
      status: normalizeTimelineStatus(data.status, "completed"),
      latencyMillis: Number(data.latencyMillis ?? 0)
    };
  }
  if (eventName === "llm_delta") {
    return {
      type: "llm",
      status: normalizeTimelineStatus(data.status, "completed"),
      modelName: text(data.modelName) || "模型调用",
      tokens: Number(data.totalTokens ?? 0),
      latencyMillis: Number(data.latencyMillis ?? 0)
    };
  }
  if (eventName === "run_done") {
    return {
      type: "run",
      status: "completed",
      title: "运行完成",
      content: data.durationMillis ? `${data.durationMillis} ms` : ""
    };
  }
  if (eventName === "run_error") {
    return {
      type: "run",
      status: "error",
      title: "运行失败",
      content: normalizeMessage(data.errorMessage || data.message, "处理失败")
    };
  }
  if (eventName === "quota_delta") {
    return {
      type: "tool",
      status: "completed",
      toolName: "额度账户",
      detail: [
        text(data.quotaBalance) ? `余额 ${text(data.quotaBalance)}` : "",
        text(data.usedQuota) ? `已用 ${text(data.usedQuota)}` : "",
        text(data.frozenQuota) ? `冻结 ${text(data.frozenQuota)}` : ""
      ].filter(Boolean).join(" · ")
    };
  }
  if (eventName === "usage_metric") {
    return {
      type: "tool",
      status: "completed",
      toolName: "额度消耗",
      detail: [
        text(data.consumedQuota) ? `本次 ${text(data.consumedQuota)}` : "",
        text(data.remainingQuota) ? `剩余 ${text(data.remainingQuota)}` : "",
        text(data.modelName || data.model) ? text(data.modelName || data.model) : ""
      ].filter(Boolean).join(" · ")
    };
  }
  return null;
}

function sameToolTimelineItem(left: TimelineItem, right: TimelineItem): boolean {
  return Boolean(
    left.type === "tool"
      && right.type === "tool"
      && (
        (left.invocationId && right.invocationId && left.invocationId === right.invocationId)
          || (left.toolCallId && right.toolCallId && left.toolCallId === right.toolCallId)
      )
  );
}

export function mergeTimelineEvent(timeline: TimelineItem[] = [], item: TimelineItem | null): TimelineItem[] {
  if (!item) {
    return timeline || [];
  }
  if (item.type === "tool" && (item.invocationId || item.toolCallId)) {
    const index = timeline.findIndex((entry) => sameToolTimelineItem(entry, item));
    if (index >= 0) {
      const next = [...timeline];
      next[index] = {
        ...next[index],
        ...item,
        invocationId: item.invocationId || next[index].invocationId,
        toolCallId: item.toolCallId || next[index].toolCallId
      };
      return next;
    }
  }
  if (item.type === "run" && timelineItemStatus(item) !== "running") {
    for (let index = timeline.length - 1; index >= 0; index -= 1) {
      if (timeline[index]?.type === "run" && timelineItemStatus(timeline[index]) === "running") {
        const next = [...timeline];
        next[index] = { ...next[index], ...item };
        return next;
      }
    }
  }
  if (item.type === "flow") {
    let planBoundary = -1;
    for (let index = timeline.length - 1; index >= 0; index -= 1) {
      if (timeline[index]?.type === "plan") {
        planBoundary = index;
        break;
      }
    }
    const index = timeline.findIndex((entry, entryIndex) => (
      entryIndex > planBoundary && entry.type === "flow" && entry.stageIndex === item.stageIndex
    ));
    if (index >= 0) {
      const next = [...timeline];
      next[index] = { ...next[index], ...item };
      return next;
    }
  }
  return [...timeline, item];
}

export function replayEventsToTimeline(
  replays: unknown[] = [],
  normalizeMessage?: (message: unknown, fallback?: string) => string
): TimelineItem[] {
  const replay = [...replays].find((item) => Array.isArray(asObject(item).events) && (asObject(item).events as unknown[]).length);
  if (!replay) {
    return [];
  }
  return (((asObject(replay).events as unknown[]) || [])
    .map((event) => streamEventToTimelineItem(event, normalizeMessage))
    .filter(Boolean) as TimelineItem[])
    .reduce((timeline, item) => mergeTimelineEvent(timeline, item), [] as TimelineItem[]);
}
