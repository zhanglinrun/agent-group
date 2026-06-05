export type TimelineItem = Record<string, unknown> & {
  type: string;
  status?: string;
  invocationId?: string;
  stageIndex?: number;
};

type UnknownMap = Record<string, unknown>;

function asObject(value: unknown): UnknownMap {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as UnknownMap)
    : {};
}

function text(value: unknown): string {
  return String(value ?? "").trim();
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
      status: "completed",
      title: "运行开始",
      content: `${text(data.taskType) || "chat"} · ${text(data.model) || "bear-doctor-agent"}`
    };
  }
  if (eventName === "plan_delta") {
    return {
      type: "plan",
      status: "completed",
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
      status: (text(data.status) || "RUNNING").toLowerCase(),
      message: text(data.message),
      steps: Array.isArray(data.steps) ? data.steps : []
    };
  }
  if (eventName === "tool_call") {
    return {
      type: "tool",
      invocationId: text(data.invocationId),
      toolName: text(data.toolName) || "工具调用",
      detail: text(data.action) || text(data.argumentsJson),
      status: "running"
    };
  }
  if (eventName === "tool_result") {
    return {
      type: "tool",
      invocationId: text(data.invocationId),
      toolName: text(data.toolName) || "工具调用",
      detail: text(data.resultSummary) || text(data.errorMessage),
      status: text(data.status) === "FAILED" ? "error" : "completed",
      latencyMillis: Number(data.latencyMillis ?? 0)
    };
  }
  if (eventName === "llm_delta") {
    return {
      type: "llm",
      status: text(data.status) === "FAILED" ? "error" : "completed",
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

export function mergeTimelineEvent(timeline: TimelineItem[] = [], item: TimelineItem | null): TimelineItem[] {
  if (!item) {
    return timeline || [];
  }
  if (item.type === "tool" && item.invocationId) {
    const index = timeline.findIndex((entry) => entry.type === "tool" && entry.invocationId === item.invocationId);
    if (index >= 0) {
      const next = [...timeline];
      next[index] = { ...next[index], ...item };
      return next;
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
